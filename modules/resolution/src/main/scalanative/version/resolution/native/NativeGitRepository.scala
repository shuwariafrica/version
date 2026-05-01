/****************************************************************************
 * Copyright 2023-2026 Shuwari Africa Ltd.                                  *
 *                                                                          *
 * Licensed under the Apache License, Version 2.0 (the "License");          *
 * you may not use this file except in compliance with the License.         *
 * You may obtain a copy of the License at                                  *
 *                                                                          *
 *     http://www.apache.org/licenses/LICENSE-2.0                           *
 *                                                                          *
 * Unless required by applicable law or agreed to in writing, software      *
 * distributed under the License is distributed on an "AS IS" BASIS,        *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. *
 * See the License for the specific language governing permissions and      *
 * limitations under the License.                                           *
 ****************************************************************************/
package version.resolution.native

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import scala.util.boundary
import scala.util.boundary.break

import version.resolution.GitError
import version.resolution.GitRepository
import version.resolution.domain.*

// scalafix:off
/** libgit2-backed [[GitRepository]] implementation for Scala Native. */
final class NativeGitRepository private (repo: Ptr[Byte]) extends GitRepository:

  import LibGit2.*
  import LibGit2Constants.*

  private def lastError: String =
    val err = git_error_last()
    if err == null then "unknown error"
    else
      val msg = git_error_message(err)
      if msg == null then "unknown error" else fromCString(msg)

  private def oidToHex(oid: Ptr[Byte]): String =
    val buf = stackalloc[Byte](GIT_OID_SHA1_HEXSIZE + 1)
    git_oid_tostr(buf, (GIT_OID_SHA1_HEXSIZE + 1).toUSize, oid): Unit
    fromCString(buf).toLowerCase

  private def hexToOid(hex: String, oid: Ptr[Byte]): Unit =
    Zone:
      val rc = git_oid_fromstr(oid, toCString(hex))
      if rc != GIT_OK then throw RuntimeException(s"Invalid OID: $hex")

  def head: Either[GitError, Option[CommitSha]] =
    val unborn = git_repository_head_unborn(repo)
    if unborn == 1 then Right(None)
    else if unborn < 0 then Left(GitError.BackendFailure(lastError))
    else
      val refOut = stackalloc[Ptr[Byte]](1)
      val rc = git_repository_head(refOut, repo)
      if rc == GIT_EUNBORNBRANCH then Right(None)
      else if rc < 0 then Left(GitError.BackendFailure(lastError))
      else
        val ref = !refOut
        val objOut = stackalloc[Ptr[Byte]](1)
        val peelRc = git_reference_peel(objOut, ref, GIT_OBJECT_COMMIT)
        git_reference_free(ref)
        if peelRc < 0 then Left(GitError.BackendFailure(lastError))
        else
          val obj = !objOut
          val oid = git_object_id(obj)
          val hex = oidToHex(oid)
          git_object_free(obj)
          Right(Some(CommitSha(hex)))

  def resolve(rev: String): Either[GitError, CommitSha] =
    Zone:
      val objOut = stackalloc[Ptr[Byte]](1)
      val rc = git_revparse_single(objOut, repo, toCString(rev))
      if rc == GIT_ENOTFOUND then Left(GitError.RevisionNotFound(rev))
      else if rc == GIT_EAMBIGUOUS then Left(GitError.AmbiguousRevision(rev))
      else if rc < 0 then Left(GitError.BackendFailure(lastError))
      else
        val obj = !objOut
        val oid = git_object_id(obj)
        val hex = oidToHex(oid)
        git_object_free(obj)
        Right(CommitSha(hex))

  def branch: Either[GitError, Option[String]] =
    val detached = git_repository_head_detached(repo)
    if detached == 1 then Right(None)
    else
      val refOut = stackalloc[Ptr[Byte]](1)
      val rc = git_repository_head(refOut, repo)
      if rc == GIT_EUNBORNBRANCH then Right(None)
      else if rc < 0 then Left(GitError.BackendFailure(lastError))
      else
        val ref = !refOut
        val name = git_reference_shorthand(ref)
        val result = if name == null then None else Some(fromCString(name))
        git_reference_free(ref)
        Right(result)

  def isBare: Boolean = git_repository_is_bare(repo) != 0

  def clean: Either[GitError, Boolean] =
    if isBare then Right(true)
    else
      val opts = git_status_options_new()
      if opts == null then Left(GitError.BackendFailure("Failed to allocate status options"))
      else
        val listOut = stackalloc[Ptr[Byte]](1)
        val rc = git_status_list_new(listOut, repo, opts)
        git_status_options_free(opts)
        if rc < 0 then Left(GitError.BackendFailure(lastError))
        else
          val list = !listOut
          val count = git_status_list_entrycount(list)
          git_status_list_free(list)
          Right(count == 0.toUSize)

  def tags: Either[GitError, IArray[RawTag]] =
    Zone:
      val iterOut = stackalloc[Ptr[Byte]](1)
      val rc = git_reference_iterator_glob_new(iterOut, repo, toCString("refs/tags/*"))
      if rc < 0 then Left(GitError.BackendFailure(lastError))
      else
        val iter = !iterOut
        val builder = IArray.newBuilder[RawTag]
        val refOut = stackalloc[Ptr[Byte]](1)
        var iterRc = git_reference_next(refOut, iter)
        while iterRc == GIT_OK do
          val ref = !refOut
          val name = fromCString(git_reference_shorthand(ref))

          // Try peeling to tag object first to detect annotated tags
          val tagObjOut = stackalloc[Ptr[Byte]](1)
          val tagPeelRc = git_reference_peel(tagObjOut, ref, GIT_OBJECT_TAG)
          val kind = if tagPeelRc == GIT_OK then
            git_object_free(!tagObjOut)
            TagKind.Annotated
          else TagKind.Lightweight

          // Peel to commit to get the target
          val commitObjOut = stackalloc[Ptr[Byte]](1)
          val commitPeelRc = git_reference_peel(commitObjOut, ref, GIT_OBJECT_COMMIT)
          if commitPeelRc == GIT_OK then
            val commitObj = !commitObjOut
            val oid = git_object_id(commitObj)
            val hex = oidToHex(oid)
            git_object_free(commitObj)
            builder += RawTag(name, CommitSha(hex), kind)

          git_reference_free(ref)
          iterRc = git_reference_next(refOut, iter)
        end while
        git_reference_iterator_free(iter)
        Right(builder.result())
      end if

  def isAncestorOf(ancestor: CommitSha, commit: CommitSha): Either[GitError, Boolean] =
    if ancestor.value == commit.value then Right(true)
    else
      val ancestorOid = stackalloc[Byte](GIT_OID_SHA1_SIZE)
      val commitOid = stackalloc[Byte](GIT_OID_SHA1_SIZE)
      hexToOid(ancestor.value, ancestorOid)
      hexToOid(commit.value, commitOid)
      val rc = git_graph_descendant_of(repo, commitOid, ancestorOid)
      if rc == 1 then Right(true)
      else if rc == 0 then Right(false)
      else Left(GitError.BackendFailure(lastError))

  def reachableTags(from: CommitSha, tagCommits: Set[CommitSha]): Either[GitError, Set[CommitSha]] =
    if tagCommits.isEmpty then Right(Set.empty)
    else
      val walkOut = stackalloc[Ptr[Byte]](1)
      var rc = git_revwalk_new(walkOut, repo)
      if rc < 0 then Left(GitError.BackendFailure(lastError))
      else
        val walk = !walkOut
        git_revwalk_sorting(walk, GIT_SORT_TIME): Unit
        val fromOid = stackalloc[Byte](GIT_OID_SHA1_SIZE)
        hexToOid(from.value, fromOid)
        rc = git_revwalk_push(walk, fromOid)
        if rc < 0 then
          git_revwalk_free(walk)
          Left(GitError.BackendFailure(lastError))
        else
          val oidBuf = stackalloc[Byte](GIT_OID_SHA1_SIZE)
          var remaining = tagCommits
          var found = Set.empty[CommitSha]
          var walkRc = git_revwalk_next(oidBuf, walk)
          boundary:
            while walkRc == GIT_OK do
              val hex = oidToHex(oidBuf)
              val sha = CommitSha(hex)
              if remaining.contains(sha) then
                found = found + sha
                remaining = remaining - sha
                if remaining.isEmpty then break(())
              walkRc = git_revwalk_next(oidBuf, walk)
          git_revwalk_free(walk)
          Right(found)
      end if

  def walkAll(from: CommitSha, until: Option[CommitSha]): Either[GitError, IArray[RawCommit]] =
    doWalk(from, until, firstParent = false)

  def walkFirstParent(from: CommitSha, until: Option[CommitSha]): Either[GitError, IArray[RawCommit]] =
    doWalk(from, until, firstParent = true)

  private def doWalk(from: CommitSha, until: Option[CommitSha], firstParent: Boolean): Either[GitError, IArray[RawCommit]] =
    val walkOut = stackalloc[Ptr[Byte]](1)
    var rc = git_revwalk_new(walkOut, repo)
    if rc < 0 then Left(GitError.BackendFailure(lastError))
    else
      val walk = !walkOut
      git_revwalk_sorting(walk, GIT_SORT_TIME): Unit
      if firstParent then git_revwalk_simplify_first_parent(walk)
      val fromOid = stackalloc[Byte](GIT_OID_SHA1_SIZE)
      hexToOid(from.value, fromOid)
      rc = git_revwalk_push(walk, fromOid)
      if rc < 0 then
        git_revwalk_free(walk)
        Left(GitError.BackendFailure(lastError))
      else
        until.foreach: u =>
          val untilOid = stackalloc[Byte](GIT_OID_SHA1_SIZE)
          hexToOid(u.value, untilOid)
          git_revwalk_hide(walk, untilOid)

        val oidBuf = stackalloc[Byte](GIT_OID_SHA1_SIZE)
        val builder = IArray.newBuilder[RawCommit]
        var walkRc = git_revwalk_next(oidBuf, walk)
        var error: Option[GitError] = None
        while walkRc == GIT_OK && error.isEmpty do
          loadCommit(oidBuf) match
            case Right(c) =>
              builder += c
              walkRc = git_revwalk_next(oidBuf, walk)
            case Left(e) => error = Some(e)
        git_revwalk_free(walk)
        error match
          case Some(e) => Left(e)
          case None    => Right(builder.result())
    end if
  end doWalk

  private def loadCommit(oid: Ptr[Byte]): Either[GitError, RawCommit] =
    val commitOut = stackalloc[Ptr[Byte]](1)
    val rc = git_commit_lookup(commitOut, repo, oid)
    if rc < 0 then Left(GitError.ObjectNotFound(oidToHex(oid)))
    else
      val commit = !commitOut
      val hex = oidToHex(oid)
      val msg = fromCString(git_commit_message(commit))
      val time = git_commit_time(commit)
      val parentCount = git_commit_parentcount(commit).toInt
      val parents = IArray.tabulate(parentCount): i =>
        val parentOid = git_commit_parent_id(commit, i.toUInt)
        CommitSha(oidToHex(parentOid))
      git_commit_free(commit)
      Right(RawCommit(CommitSha(hex), msg, parents, time))

  def abbreviate(id: CommitSha, length: Int): Either[GitError, String] =
    val oid = stackalloc[Byte](GIT_OID_SHA1_SIZE)
    hexToOid(id.value, oid)
    val buf = stackalloc[Byte](length + 1)
    git_oid_tostr(buf, (length + 1).toUSize, oid): Unit
    Right(fromCString(buf).toLowerCase)

  def close(): Unit =
    try
      git_repository_free(repo)
      git_libgit2_shutdown(): Unit
    catch case _: Throwable => ()

end NativeGitRepository

object NativeGitRepository:

  import LibGit2.*
  import LibGit2Constants.*

  // libgit2 init/shutdown are reference-counted; every successful init must be paired with
  // a shutdown so libgit2's thread-local destructors run cleanly when the last user is gone.
  def open(path: String): Either[GitError, NativeGitRepository] =
    git_libgit2_init(): Unit
    val result = Zone:
      val repoOut = stackalloc[Ptr[Byte]](1)
      val rc = git_repository_open_ext(repoOut, toCString(path), 0.toUInt, null.asInstanceOf[CString])
      if rc == GIT_ENOTFOUND then Left(GitError.RepositoryNotFound(path))
      else if rc < 0 then
        val err = git_error_last()
        val msg = if err != null then
          val m = git_error_message(err)
          if m != null then fromCString(m) else "unknown error"
        else "unknown error"
        Left(GitError.BackendFailure(msg))
      else Right(new NativeGitRepository(!repoOut))
    if result.isLeft then git_libgit2_shutdown(): Unit
    result
// scalafix:on
