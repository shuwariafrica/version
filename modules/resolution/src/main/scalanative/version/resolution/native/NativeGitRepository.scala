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

import boilerplate.Slice

import java.nio.charset.StandardCharsets

import scala.annotation.threadUnsafe
import scala.collection.mutable
import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import scala.util.boundary
import scala.util.boundary.break

import version.resolution.GitError
import version.resolution.GitRepository
import version.resolution.GpgSigner
import version.resolution.domain.*

// scalafix:off
/** [[GitRepository]] over a libgit2 `git_repository`; see [[NativeGitRepository$ NativeGitRepository]] to obtain one. */
final class NativeGitRepository private (repo: Ptr[Byte]) extends GitRepository:

  import LibGit2.*
  import LibGit2Constants.*
  import NativeGitRepository.*

  @threadUnsafe private var closed: Boolean = false

  // Declare FFI buffer slots at function entry: only entry-block allocas get hoisted by release-fast's inliner.

  def head: Either[GitError, Option[CommitSha]] =
    val refOut = stackalloc[Ptr[Byte]](1)
    val objOut = stackalloc[Ptr[Byte]](1)
    val unborn = git_repository_head_unborn(repo)
    if unborn == 1 then Right(None)
    else if unborn < 0 then Left(GitError.BackendFailure(lastError))
    else
      val rc = git_repository_head(refOut, repo)
      if rc == GIT_EUNBORNBRANCH then Right(None)
      else if rc < 0 then Left(GitError.BackendFailure(lastError))
      else
        val ref = !refOut
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
    val refOut = stackalloc[Ptr[Byte]](1)
    val detached = git_repository_head_detached(repo)
    if detached == 1 then Right(None)
    else
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

  def gitDir: String = directoryPath(git_repository_path(repo))

  def workTree: Option[String] =
    val workdir = git_repository_workdir(repo)
    if workdir == null then None else Some(directoryPath(workdir))

  def clean: Either[GitError, Boolean] =
    val rc = git_workdir_dirty_count(repo)
    if rc < 0 then Left(GitError.BackendFailure(lastError))
    else Right(rc == 0)

  def tags: Either[GitError, IArray[RawTag]] =
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
    val ancestorOid = stackalloc[Byte](GIT_OID_SHA1_SIZE)
    val commitOid = stackalloc[Byte](GIT_OID_SHA1_SIZE)
    if ancestor.value == commit.value then Right(true)
    else
      hexToOid(ancestor.value, ancestorOid)
      hexToOid(commit.value, commitOid)
      val rc = git_graph_descendant_of(repo, commitOid, ancestorOid)
      if rc == 1 then Right(true)
      else if rc == 0 then Right(false)
      else Left(GitError.BackendFailure(lastError))

  def reachableTags(from: CommitSha, tagCommits: Set[CommitSha]): Either[GitError, Set[CommitSha]] =
    val walkOut = stackalloc[Ptr[Byte]](1)
    val fromOid = stackalloc[Byte](GIT_OID_SHA1_SIZE)
    val oidBuf = stackalloc[Byte](GIT_OID_SHA1_SIZE)
    if tagCommits.isEmpty then Right(Set.empty)
    else
      val rc = git_revwalk_new(walkOut, repo)
      if rc < 0 then Left(GitError.BackendFailure(lastError))
      else
        val walk = !walkOut
        val sortRc = git_revwalk_sorting(walk, GIT_SORT_TIME)
        hexToOid(from.value, fromOid)
        val pushRc = if sortRc < 0 then sortRc else git_revwalk_push(walk, fromOid)
        if pushRc < 0 then
          git_revwalk_free(walk)
          Left(GitError.BackendFailure(lastError))
        else
          // Membership is tested once per commit of the walk, so the sets hold plain hex and stay mutable.
          val remaining = mutable.HashSet.from(tagCommits.iterator.map(_.value))
          val found = mutable.HashSet.empty[String]
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
    end if
  end reachableTags

  def walkAll(from: CommitSha, until: Option[CommitSha]): Either[GitError, IArray[RawCommit]] =
    doWalk(from, until, firstParent = false)

  def walkFirstParent(from: CommitSha, until: Option[CommitSha]): Either[GitError, IArray[RawCommit]] =
    doWalk(from, until, firstParent = true)

  private def doWalk(from: CommitSha, until: Option[CommitSha], firstParent: Boolean): Either[GitError, IArray[RawCommit]] =
    val walkOut = stackalloc[Ptr[Byte]](1)
    val fromOid = stackalloc[Byte](GIT_OID_SHA1_SIZE)
    val untilOid = stackalloc[Byte](GIT_OID_SHA1_SIZE)
    val oidBuf = stackalloc[Byte](GIT_OID_SHA1_SIZE)
    val rc = git_revwalk_new(walkOut, repo)
    if rc < 0 then Left(GitError.BackendFailure(lastError))
    else
      val walk = !walkOut
      val sortRc = git_revwalk_sorting(walk, GIT_SORT_TIME)
      if firstParent then git_revwalk_simplify_first_parent(walk)
      hexToOid(from.value, fromOid)
      val pushRc = if sortRc < 0 then sortRc else git_revwalk_push(walk, fromOid)
      if pushRc < 0 then
        git_revwalk_free(walk)
        Left(GitError.BackendFailure(lastError))
      else
        val hideRc = until match
          case Some(u) =>
            hexToOid(u.value, untilOid)
            git_revwalk_hide(walk, untilOid)
          case None => GIT_OK
        if hideRc < 0 then
          git_revwalk_free(walk)
          Left(GitError.BackendFailure(lastError))
        else
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

  def loadCommit(sha: CommitSha): Either[GitError, RawCommit] =
    val oidBuf = stackalloc[Byte](GIT_OID_SHA1_SIZE)
    hexToOid(sha.value, oidBuf)
    loadCommit(oidBuf)

  private def loadCommit(oid: Ptr[Byte]): Either[GitError, RawCommit] =
    val commitOut = stackalloc[Ptr[Byte]](1)
    val lookupRc = git_commit_lookup(commitOut, repo, oid)
    if lookupRc < 0 then Left(GitError.ObjectNotFound(oidToHex(oid)))
    else
      val commit = !commitOut
      val hex = oidToHex(oid)
      val msg = fromCString(git_commit_message(commit))
      val time = git_commit_time(commit)
      val parentCount = git_commit_parentcount(commit).toInt
      // Once per commit loaded: sizing the array up front skips the Lambda$ that IArray.newBuilder emits into the NIR.
      val parentArr = new Array[CommitSha](parentCount)
      var p = 0
      while p < parentCount do
        val parentOid = git_commit_parent_id(commit, p.toUInt)
        parentArr(p) = CommitSha(oidToHex(parentOid))
        p += 1
      val parents = IArray.unsafeFromArray(parentArr)
      git_commit_free(commit)
      Right(RawCommit(CommitSha(hex), msg, parents, time))
  end loadCommit

  def loadTagger(name: String): Either[GitError, Long] =
    Zone:
      val refOut = stackalloc[Ptr[Byte]](1)
      val tagObjOut = stackalloc[Ptr[Byte]](1)
      if git_reference_lookup(refOut, repo, toCString("refs/tags/" + name)) < 0 then Left(GitError.ObjectNotFound(name))
      else
        val ref = !refOut
        val peelRc = git_reference_peel(tagObjOut, ref, GIT_OBJECT_TAG)
        git_reference_free(ref)
        if peelRc < 0 then Left(GitError.BackendFailure(lastError))
        else
          val tagObj = !tagObjOut
          val sig = git_tag_tagger(tagObj)
          // git_signature is owned by the tag object: read its epoch seconds (when.time) before freeing the tag.
          val time = if sig == null then 0L else sig.at3._1
          git_object_free(tagObj)
          Right(time)

  def signingKey: Either[GitError, Option[String]] =
    val cfgOut = stackalloc[Ptr[Byte]](1)
    if git_repository_config_snapshot(cfgOut, repo) < 0 then Left(GitError.BackendFailure(lastError))
    else
      val cfg = !cfgOut
      val valueOut = stackalloc[CString](1)
      val getRc = git_config_get_string(valueOut, cfg, c"user.signingkey")
      // The snapshot owns the returned string, so copy before freeing it.
      val outcome =
        if getRc == GIT_OK then
          val value = fromCString(!valueOut)
          Right(if value.nonEmpty then Some(value) else None)
        else if getRc == GIT_ENOTFOUND then Right(None)
        else Left(GitError.BackendFailure(lastError))
      git_config_free(cfg)
      outcome

  def defaultSignature: Either[GitError, AuthorSignature] =
    val cfgOut = stackalloc[Ptr[Byte]](1)
    if git_repository_config_snapshot(cfgOut, repo) < 0 then Left(GitError.BackendFailure(lastError))
    else
      val cfg = !cfgOut
      val nameOut = stackalloc[CString](1)
      val emailOut = stackalloc[CString](1)
      val nameRc = git_config_get_string(nameOut, cfg, c"user.name")
      val emailRc = git_config_get_string(emailOut, cfg, c"user.email")
      // Copy values out before freeing the config that owns them.
      val outcome =
        if nameRc == GIT_OK && emailRc == GIT_OK then
          Right(AuthorSignature(fromCString(!nameOut), fromCString(!emailOut), System.currentTimeMillis() / 1000L, 0))
        else Left(GitError.BackendFailure("user.name and user.email must be configured"))
      git_config_free(cfg)
      outcome

  def createCommit(message: String, author: AuthorSignature, sign: Boolean): Either[GitError, CommitSha] =
    if sign then signedCommit(message, author)
    else
      Zone:
        val headOut = stackalloc[Ptr[Byte]](1)
        val treeOut = stackalloc[Ptr[Byte]](1)
        val sigOut = stackalloc[Ptr[Byte]](1)
        val parents = stackalloc[Ptr[Byte]](1)
        val newOid = stackalloc[Byte](GIT_OID_SHA1_SIZE)
        if git_revparse_single(headOut, repo, c"HEAD") < 0 then Left(GitError.BackendFailure(lastError))
        else
          val head = !headOut
          if git_commit_tree(treeOut, head) < 0 then
            git_object_free(head)
            Left(GitError.BackendFailure(lastError))
          else
            val tree = !treeOut
            val sigRc =
              git_signature_new(sigOut, toCString(author.name), toCString(author.email), author.whenEpochSeconds, author.offsetMinutes)
            if sigRc < 0 then
              git_tree_free(tree)
              git_object_free(head)
              Left(GitError.BackendFailure(lastError))
            else
              val sig = !sigOut
              !parents = head
              val createRc =
                git_commit_create(newOid, repo, c"HEAD", sig, sig, null.asInstanceOf[CString], toCString(message), tree, 1.toUSize, parents)
              git_signature_free(sig)
              git_tree_free(tree)
              git_object_free(head)
              if createRc < 0 then Left(GitError.BackendFailure(lastError))
              else Right(CommitSha(oidToHex(newOid)))
        end if

  // Hand-built rather than through git_commit_create so that both backends sign along the one GpgSigner path.
  private def signedCommit(message: String, author: AuthorSignature): Either[GitError, CommitSha] =
    signingKey.flatMap:
      case None      => Left(GitError.SigningFailure("signing requested but user.signingkey is not configured"))
      case Some(key) => signedCommitWith(message, author, key)

  private def signedCommitWith(message: String, author: AuthorSignature, key: String): Either[GitError, CommitSha] =
    Zone:
      val headOut = stackalloc[Ptr[Byte]](1)
      val treeOut = stackalloc[Ptr[Byte]](1)
      val sigOut = stackalloc[Ptr[Byte]](1)
      val parents = stackalloc[Ptr[Byte]](1)
      val buf = stackalloc[GitBuf](1)
      val newOid = stackalloc[Byte](GIT_OID_SHA1_SIZE)
      val refOut = stackalloc[Ptr[Byte]](1)
      val newRefOut = stackalloc[Ptr[Byte]](1)
      // Zero git_buf before git_commit_create_buffer: a garbage ptr would later make git_buf_dispose free a wild pointer.
      buf._1 = null.asInstanceOf[CString]
      buf._2 = 0.toUSize
      buf._3 = 0.toUSize
      if git_revparse_single(headOut, repo, c"HEAD") < 0 then Left(GitError.BackendFailure(lastError))
      else
        val head = !headOut
        if git_commit_tree(treeOut, head) < 0 then
          git_object_free(head)
          Left(GitError.BackendFailure(lastError))
        else
          val tree = !treeOut
          val sigRc =
            git_signature_new(sigOut, toCString(author.name), toCString(author.email), author.whenEpochSeconds, author.offsetMinutes)
          if sigRc < 0 then
            git_tree_free(tree)
            git_object_free(head)
            Left(GitError.BackendFailure(lastError))
          else
            val sig = !sigOut
            !parents = head
            val bufRc =
              git_commit_create_buffer(buf, repo, sig, sig, null.asInstanceOf[CString], toCString(message), tree, 1.toUSize, parents)
            git_signature_free(sig)
            git_tree_free(tree)
            git_object_free(head)
            if bufRc < 0 then
              git_buf_dispose(buf)
              Left(GitError.BackendFailure(lastError))
            else
              val content = readBuf(buf)
              GpgSigner.sign(content, key) match
                case Left(error) =>
                  git_buf_dispose(buf)
                  Left(error)
                case Right(signature) =>
                  val createRc = git_commit_create_with_signature(newOid, repo, buf._1, toCString(signature), null.asInstanceOf[CString])
                  git_buf_dispose(buf)
                  if createRc < 0 then Left(GitError.BackendFailure(lastError))
                  else if git_repository_head(refOut, repo) < 0 then Left(GitError.BackendFailure(lastError))
                  else
                    val ref = !refOut
                    val setRc = git_reference_set_target(newRefOut, ref, newOid, toCString(s"commit (signed): ${firstLine(message)}"))
                    git_reference_free(ref)
                    if setRc < 0 then Left(GitError.BackendFailure(lastError))
                    else
                      git_reference_free(!newRefOut)
                      Right(CommitSha(oidToHex(newOid)))
          end if
        end if
      end if

  private def readBuf(buf: Ptr[GitBuf]): Array[Byte] = Slice.of(buf._1, buf._3.toInt).toArray

  private inline def firstLine(s: String): String = s.takeWhile(_ != '\n')

  def createTag(name: String, target: CommitSha, message: String, tagger: AuthorSignature, sign: Boolean): Either[GitError, Unit] =
    if sign then signedTag(name, target, message, tagger)
    else
      Zone:
        val commitOut = stackalloc[Ptr[Byte]](1)
        val sigOut = stackalloc[Ptr[Byte]](1)
        val targetOid = stackalloc[Byte](GIT_OID_SHA1_SIZE)
        val tagOid = stackalloc[Byte](GIT_OID_SHA1_SIZE)
        hexToOid(target.value, targetOid)
        if git_commit_lookup(commitOut, repo, targetOid) < 0 then Left(GitError.ObjectNotFound(target.value))
        else
          val commit = !commitOut
          val sigRc =
            git_signature_new(sigOut, toCString(tagger.name), toCString(tagger.email), tagger.whenEpochSeconds, tagger.offsetMinutes)
          if sigRc < 0 then
            git_commit_free(commit)
            Left(GitError.BackendFailure(lastError))
          else
            val sig = !sigOut
            val createRc = git_tag_create(tagOid, repo, toCString(name), commit, sig, toCString(message), 0)
            git_signature_free(sig)
            git_commit_free(commit)
            if createRc < 0 then Left(GitError.BackendFailure(lastError))
            else Right(())

  // The signature must cover the exact bytes of the tag object, so the payload is assembled here rather than by
  // git_tag_create, and its message keeps the trailing newline that puts the signature on a line of its own.
  private def signedTag(name: String, target: CommitSha, message: String, tagger: AuthorSignature): Either[GitError, Unit] =
    signingKey.flatMap:
      case None      => Left(GitError.SigningFailure("signing requested but user.signingkey is not configured"))
      case Some(key) =>
        val payload = tagPayload(name, target, message, tagger)
        GpgSigner
          .sign(payload.getBytes(StandardCharsets.UTF_8), key)
          .flatMap: signature =>
            val full = payload + signature + "\n"
            Zone:
              val tagOid = stackalloc[Byte](GIT_OID_SHA1_SIZE)
              if git_tag_create_from_buffer(tagOid, repo, toCString(full), 0) < 0 then Left(GitError.BackendFailure(lastError))
              else Right(())

  private def tagPayload(name: String, target: CommitSha, message: String, tagger: AuthorSignature): String =
    val body = if message.endsWith("\n") then message else message + "\n"
    s"object ${target.value}\ntype commit\ntag $name\ntagger ${taggerLine(tagger)}\n\n$body"

  private def taggerLine(sig: AuthorSignature): String =
    val absMinutes = math.abs(sig.offsetMinutes)
    val sign = if sig.offsetMinutes < 0 then "-" else "+"
    val zone = f"$sign${absMinutes / 60}%02d${absMinutes % 60}%02d"
    s"${sig.name} <${sig.email}> ${sig.whenEpochSeconds} $zone"

  def close(): Unit =
    if !closed then
      closed = true
      git_repository_free(repo)
      // libgit2's init is reference-counted and every factory takes one: closing the last repository releases the
      // library's thread-local state, which would otherwise linger for the life of the process.
      git_libgit2_shutdown(): Unit

end NativeGitRepository

/** Factories for [[NativeGitRepository]], backing this platform's `openRepository` and `discoverRepository`. */
object NativeGitRepository:

  import LibGit2.*
  import LibGit2Constants.*

  // Once per commit and tag of a walk: writing hex here replaces the git_oid_tostr, fromCString and toLowerCase
  // chain, and libgit2 itself emits this same lowercase alphabet.
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

  // Unguarded because CommitSha.validate has already established 40 characters of lowercase hex.
  private inline def hexToOid(hex: String, oid: Ptr[Byte]): Unit =
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

  private def lastError: String =
    val err = git_error_last()
    if err == null then "unknown error"
    else
      val msg = git_error_message(err)
      if msg == null then "unknown error" else fromCString(msg)

  // libgit2 reports directories with a trailing separator; the trait's paths carry none.
  private def directoryPath(path: CString): String = fromCString(path).stripSuffix("/")

  def open(path: String): Either[GitError, NativeGitRepository] =
    openExt(path, GIT_REPOSITORY_OPEN_NO_SEARCH, None)

  def discover(start: String): Either[GitError, NativeGitRepository] =
    openExt(start, 0, None)

  def discover(start: String, ceilings: Seq[String]): Either[GitError, NativeGitRepository] =
    openExt(start, 0, if ceilings.isEmpty then None else Some(ceilings.mkString(java.io.File.pathSeparator)))

  private def openExt(path: String, flags: Int, ceilingDirs: Option[String]): Either[GitError, NativeGitRepository] =
    git_libgit2_init(): Unit
    // Repository ownership is never validated: no caller opens an adversarial repository, and the check rejects the
    // UID-mismatched bind mounts CI routinely presents. The effect is process-wide and idempotent.
    git_libgit2_opts(GIT_OPT_SET_OWNER_VALIDATION, 0): Unit
    val result = Zone:
      val repoOut = stackalloc[Ptr[Byte]](1)
      val ceilings = ceilingDirs.fold(null.asInstanceOf[CString])(toCString)
      val rc = git_repository_open_ext(repoOut, toCString(path), flags.toUInt, ceilings)
      if rc == GIT_ENOTFOUND then Left(GitError.RepositoryNotFound(path))
      else if rc < 0 then Left(GitError.BackendFailure(lastError))
      else Right(new NativeGitRepository(!repoOut))
    if result.isLeft then git_libgit2_shutdown(): Unit
    result
end NativeGitRepository
// scalafix:on
