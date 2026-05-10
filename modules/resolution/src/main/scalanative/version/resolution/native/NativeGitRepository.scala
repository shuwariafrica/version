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

import scala.annotation.threadUnsafe
import scala.collection.mutable
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
  import NativeGitRepository.*

  @threadUnsafe private var closed: Boolean = false

  private inline def lastError: String =
    val err = git_error_last()
    if err == null then "unknown error"
    else
      val msg = git_error_message(err)
      if msg == null then "unknown error" else fromCString(msg)

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
          val hex = oidToHex(git_object_id(obj))
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
        val hex = oidToHex(git_object_id(obj))
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
    val rc = git_workdir_dirty_count(repo)
    if rc < 0 then Left(GitError.BackendFailure(lastError))
    else Right(rc == 0)

  def tags: Either[GitError, IArray[RawTag]] =
    // c"..." is a compile-time-constant CString - no Zone, no toCString
    // allocation. The four Ptr[Byte] slots are hoisted to function entry so
    // each ref iteration reuses the same stack storage; LLVM does not
    // promote allocas declared inside the loop body to entry under
    // release-fast.
    val iterOut = stackalloc[Ptr[Byte]](1)
    val refOut = stackalloc[Ptr[Byte]](1)
    val tagObjOut = stackalloc[Ptr[Byte]](1)
    val commitObjOut = stackalloc[Ptr[Byte]](1)
    val rc = git_reference_iterator_glob_new(iterOut, repo, c"refs/tags/*")
    if rc < 0 then Left(GitError.BackendFailure(lastError))
    else
      val iter = !iterOut
      val builder = IArray.newBuilder[RawTag]
      var iterRc = git_reference_next(refOut, iter)
      while iterRc == GIT_OK do
        val ref = !refOut
        val name = fromCString(git_reference_shorthand(ref))

        val tagPeelRc = git_reference_peel(tagObjOut, ref, GIT_OBJECT_TAG)
        val kind =
          if tagPeelRc == GIT_OK then
            git_object_free(!tagObjOut)
            TagKind.Annotated
          else TagKind.Lightweight

        val commitPeelRc = git_reference_peel(commitObjOut, ref, GIT_OBJECT_COMMIT)
        if commitPeelRc == GIT_OK then
          val commitObj = !commitObjOut
          val hex = oidToHex(git_object_id(commitObj))
          git_object_free(commitObj)
          builder += RawTag(name, CommitSha(hex), kind)

        git_reference_free(ref)
        iterRc = git_reference_next(refOut, iter)
      end while
      git_reference_iterator_free(iter)
      Right(builder.result())
    end if
  end tags

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
      val rc = git_revwalk_new(walkOut, repo)
      if rc < 0 then Left(GitError.BackendFailure(lastError))
      else
        val walk = !walkOut
        git_revwalk_sorting(walk, GIT_SORT_TIME): Unit
        val fromOid = stackalloc[Byte](GIT_OID_SHA1_SIZE)
        hexToOid(from.value, fromOid)
        val pushRc = git_revwalk_push(walk, fromOid)
        if pushRc < 0 then
          git_revwalk_free(walk)
          Left(GitError.BackendFailure(lastError))
        else
          // Hotpath: a mutable HashSet of the underlying String values keeps
          // the inner per-commit lookup at amortised O(1) and avoids the
          // immutable Set node allocation that the previous implementation
          // paid for every visited commit.
          val remaining = mutable.HashSet.from(tagCommits.iterator.map(_.value))
          val found = mutable.HashSet.empty[String]
          val oidBuf = stackalloc[Byte](GIT_OID_SHA1_SIZE)
          var walkRc = git_revwalk_next(oidBuf, walk)
          boundary:
            while walkRc == GIT_OK do
              val hex = oidToHex(oidBuf)
              if remaining.remove(hex) then
                found += hex
                if remaining.isEmpty then break(())
              walkRc = git_revwalk_next(oidBuf, walk)
          git_revwalk_free(walk)
          val outcome = found.iterator.map(CommitSha.apply).toSet
          Right(outcome)
      end if

  def walkAll(from: CommitSha, until: Option[CommitSha]): Either[GitError, IArray[RawCommit]] =
    doWalk(from, until, firstParent = false)

  def walkFirstParent(from: CommitSha, until: Option[CommitSha]): Either[GitError, IArray[RawCommit]] =
    doWalk(from, until, firstParent = true)

  private def doWalk(from: CommitSha, until: Option[CommitSha], firstParent: Boolean): Either[GitError, IArray[RawCommit]] =
    val walkOut = stackalloc[Ptr[Byte]](1)
    val rc = git_revwalk_new(walkOut, repo)
    if rc < 0 then Left(GitError.BackendFailure(lastError))
    else
      val walk = !walkOut
      git_revwalk_sorting(walk, GIT_SORT_TIME): Unit
      if firstParent then git_revwalk_simplify_first_parent(walk)
      val fromOid = stackalloc[Byte](GIT_OID_SHA1_SIZE)
      hexToOid(from.value, fromOid)
      val pushRc = git_revwalk_push(walk, fromOid)
      if pushRc < 0 then
        git_revwalk_free(walk)
        Left(GitError.BackendFailure(lastError))
      else
        val hideRc = until match
          case Some(u) =>
            val untilOid = stackalloc[Byte](GIT_OID_SHA1_SIZE)
            hexToOid(u.value, untilOid)
            git_revwalk_hide(walk, untilOid)
          case None => GIT_OK
        if hideRc < 0 then
          git_revwalk_free(walk)
          Left(GitError.BackendFailure(lastError))
        else
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
    end if
  end doWalk

  private def loadCommit(oid: Ptr[Byte]): Either[GitError, RawCommit] =
    val commitOut = stackalloc[Ptr[Byte]](1)
    val lookupRc = git_commit_lookup(commitOut, repo, oid)
    if lookupRc < 0 then Left(GitError.ObjectNotFound(oidToHex(oid)))
    else
      val commit = !commitOut
      val hex = oidToHex(oid)
      val msg = fromCString(git_commit_message(commit))
      val time = git_commit_time(commit).toInt
      val parentCount = git_commit_parentcount(commit).toInt
      // Hotpath: parentCount is known up-front, so a fixed Array[CommitSha]
      // sized exactly to the count beats IArray.newBuilder, whose closure
      // construction shows up as Lambda$ heap allocation in IR.
      val parentArr = new Array[CommitSha](parentCount)
      var p = 0
      while p < parentCount do
        val parentOid = git_commit_parent_id(commit, p.toUInt)
        parentArr(p) = CommitSha(oidToHex(parentOid))
        p += 1
      val parents = IArray.unsafeFromArray(parentArr)
      git_commit_free(commit)
      Right(RawCommit(CommitSha(hex), msg, parents, time))

  def abbreviate(id: CommitSha, length: Int): Either[GitError, String] =
    val v = id.value
    val n = if length < 0 then 0 else if length > v.length then v.length else length
    Right(v.substring(0, n))

  def close(): Unit =
    if !closed then
      closed = true
      git_repository_free(repo)
      // Pair with the git_libgit2_init() in `open` so libgit2's thread-local
      // destructors run cleanly when the last user closes; without this every
      // successful open leaks a refcount and per-thread state lingers for the
      // session lifetime.
      git_libgit2_shutdown(): Unit

end NativeGitRepository

/** Factories and FFI utilities for [[NativeGitRepository]]. */
object NativeGitRepository:

  import LibGit2.*
  import LibGit2Constants.*

  // libgit2 emits OIDs as lowercase ASCII hex (the upstream git_oid_fmt_substr
  // helper writes from the constant table "0123456789abcdef"); we replicate
  // that contract directly here, reading the raw 20 SHA-1 bytes via Ptr[Byte]
  // and writing 40 chars into a single Array[Char] for one String allocation.
  // Bypasses the previous chain of 41-byte stackalloc, memset, FFI call to
  // git_oid_tostr, fromCString's UTF-8 charset decode, and a redundant
  // .toLowerCase pass on already-lowercase output.
  private inline def oidToHex(oid: Ptr[Byte]): String =
    val chars = new Array[Char](GIT_OID_SHA1_HEXSIZE)
    var i = 0
    while i < GIT_OID_SHA1_SIZE do
      val b = (!(oid + i)).toInt & 0xff
      val hi = b >>> 4
      val lo = b & 0x0f
      chars(2 * i) = (if hi < 10 then hi + '0' else hi + 'a' - 10).toChar
      chars(2 * i + 1) = (if lo < 10 then lo + '0' else lo + 'a' - 10).toChar
      i += 1
    new String(chars)

  // Decode a 40-char hex SHA into the 20 raw bytes of a git_oid buffer in
  // place. CommitSha.validate already constrains input to hex characters but
  // not length, so the length re-check below guards against silent truncation
  // or overrun of the OID buffer. Per-char validation is intentionally
  // absent: hexNibble is only reachable through CommitSha-validated paths.
  // Kept as a non-inline def so the cold RuntimeException's IR sits in one
  // function rather than fanning out to every caller.
  private def hexToOid(hex: String, oid: Ptr[Byte]): Unit =
    if hex.length != GIT_OID_SHA1_HEXSIZE then throw RuntimeException("CommitSha length is not 40 hex characters")
    var i = 0
    while i < GIT_OID_SHA1_SIZE do
      val hi = hexNibble(hex.charAt(2 * i))
      val lo = hexNibble(hex.charAt(2 * i + 1))
      !(oid + i) = ((hi << 4) | lo).toByte
      i += 1

  private inline def hexNibble(c: Char): Int =
    if c >= '0' && c <= '9' then c - '0'
    else if c >= 'a' && c <= 'f' then c - 'a' + 10
    else c - 'A' + 10

  /** Opens a libgit2-backed [[NativeGitRepository]] at the given path. */
  // libgit2 init/shutdown are reference-counted; pair every successful init
  // with a shutdown so per-thread destructors run when the last user closes.
  def open(path: String): Either[GitError, NativeGitRepository] =
    git_libgit2_init(): Unit
    val result = Zone:
      val repoOut = stackalloc[Ptr[Byte]](1)
      val rc = git_repository_open_ext(repoOut, toCString(path), 0.toUInt, null.asInstanceOf[CString])
      if rc == GIT_ENOTFOUND then Left(GitError.RepositoryNotFound(path))
      else if rc < 0 then
        val err = git_error_last()
        val msg =
          if err != null then
            val m = git_error_message(err)
            if m != null then fromCString(m) else "unknown error"
          else "unknown error"
        Left(GitError.BackendFailure(msg))
      else Right(new NativeGitRepository(!repoOut))
    if result.isLeft then git_libgit2_shutdown(): Unit
    result
end NativeGitRepository
// scalafix:on
