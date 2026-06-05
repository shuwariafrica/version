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
package version.resolution.jvm

import boilerplate.nullable.*
import org.eclipse.jgit.api.Git as JGit
import org.eclipse.jgit.errors.AmbiguousObjectException
import org.eclipse.jgit.errors.IncorrectObjectTypeException
import org.eclipse.jgit.errors.MissingObjectException
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.errors.RevisionSyntaxException
import org.eclipse.jgit.lib.CommitBuilder
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.GpgConfig
import org.eclipse.jgit.lib.GpgSignature
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.RefUpdate
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.TagBuilder
import org.eclipse.jgit.lib.UserConfig
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder

import scala.annotation.threadUnsafe
import scala.util.Using
import scala.util.boundary
import scala.util.boundary.break

import version.resolution.GitError
import version.resolution.GitRepository
import version.resolution.GpgSigner
import version.resolution.domain.*

// scalafix:off
/** JGit-backed [[GitRepository]] implementation for the JVM platform. */
final class JvmGitRepository private (repo: Repository) extends GitRepository:

  @threadUnsafe private var closed: Boolean = false

  def head: Either[GitError, Option[CommitSha]] =
    try Right(repo.exactRef(Constants.HEAD).option.flatMap(ref => ref.getLeaf.getObjectId.option).map(oid => CommitSha(oid.name)))
    catch case e: java.io.IOException => Left(GitError.BackendFailure(e.getMessage.unsafe))

  def resolve(rev: String): Either[GitError, CommitSha] =
    try repo.resolve(s"$rev^{commit}").either(GitError.RevisionNotFound(rev)).map(oid => CommitSha(oid.name))
    catch
      case e: AmbiguousObjectException => Left(GitError.AmbiguousRevision(rev))
      case _: RevisionSyntaxException  => Left(GitError.RevisionNotFound(rev))
      case e: java.io.IOException      => Left(GitError.BackendFailure(e.getMessage.unsafe))

  def branch: Either[GitError, Option[String]] =
    try Right(repo.exactRef(Constants.HEAD).option.filter(_.isSymbolic).map(ref => Repository.shortenRefName(ref.getTarget.getName)))
    catch case e: java.io.IOException => Left(GitError.BackendFailure(e.getMessage.unsafe))

  def isBare: Boolean = repo.isBare

  def clean: Either[GitError, Boolean] =
    if isBare then Right(true)
    else
      try
        Using.resource(new JGit(repo)): git =>
          Right(git.status().call().isClean)
      catch
        case e: org.eclipse.jgit.api.errors.GitAPIException =>
          Left(GitError.BackendFailure(e.getMessage.unsafe))
        case e: java.io.IOException =>
          Left(GitError.BackendFailure(e.getMessage.unsafe))

  def tags: Either[GitError, IArray[RawTag]] =
    try
      val refDb = repo.getRefDatabase
      val refs = refDb.getRefsByPrefix(Constants.R_TAGS)
      val builder = IArray.newBuilder[RawTag]
      val iter = refs.iterator
      while iter.hasNext do
        val ref = iter.next().unsafe
        val peeled = refDb.peel(ref)
        val (commitOid, kind) =
          peeled.getPeeledObjectId.fold((peeled.getObjectId, TagKind.Lightweight))(p => (p, TagKind.Annotated))
        commitOid.fold(()): co =>
          builder += RawTag(ref.getName.unsafe.stripPrefix(Constants.R_TAGS), CommitSha(co.name), kind): Unit
      Right(builder.result())
    catch case e: java.io.IOException => Left(GitError.BackendFailure(e.getMessage.unsafe))

  def isAncestorOf(ancestor: CommitSha, commit: CommitSha): Either[GitError, Boolean] =
    if ancestor.value == commit.value then Right(true)
    else
      try
        Using.resource(new RevWalk(repo)): rw =>
          val base = rw.parseCommit(ObjectId.fromString(ancestor.value))
          val tip = rw.parseCommit(ObjectId.fromString(commit.value))
          Right(rw.isMergedInto(base, tip))
      catch
        case e: MissingObjectException       => Left(GitError.ObjectNotFound(e.getObjectId.unsafe.name))
        case e: IncorrectObjectTypeException => Left(GitError.BackendFailure(e.getMessage.unsafe))
        case e: java.io.IOException          => Left(GitError.BackendFailure(e.getMessage.unsafe))

  def reachableTags(from: CommitSha, tagCommits: Set[CommitSha]): Either[GitError, Set[CommitSha]] =
    if tagCommits.isEmpty then Right(Set.empty)
    else
      try
        Using.resource(new RevWalk(repo)): rw =>
          val fromOid = ObjectId.fromString(from.value)
          rw.markStart(rw.parseCommit(fromOid))
          var remaining = tagCommits
          var found = Set.empty[CommitSha]
          boundary:
            // Walk loop: rw.next() returns the null sentinel at end-of-walk. The raw check avoids an Option allocation
            // per commit in this hot iteration and lets explicit-nulls flow-type `c` as non-null inside the body.
            var c = rw.next()
            while c != null do
              val sha = CommitSha(c.getId.name)
              if remaining.contains(sha) then
                found = found + sha
                remaining = remaining - sha
                if remaining.isEmpty then break(())
              c = rw.next()
          Right(found)
      catch
        case e: MissingObjectException       => Left(GitError.ObjectNotFound(e.getObjectId.unsafe.name))
        case e: IncorrectObjectTypeException => Left(GitError.BackendFailure(e.getMessage.unsafe))
        case e: java.io.IOException          => Left(GitError.BackendFailure(e.getMessage.unsafe))

  def walkAll(from: CommitSha, until: Option[CommitSha]): Either[GitError, IArray[RawCommit]] =
    doWalk(from, until, firstParent = false)

  def walkFirstParent(from: CommitSha, until: Option[CommitSha]): Either[GitError, IArray[RawCommit]] =
    doWalk(from, until, firstParent = true)

  private def doWalk(from: CommitSha, until: Option[CommitSha], firstParent: Boolean): Either[GitError, IArray[RawCommit]] =
    try
      Using.resource(new RevWalk(repo)): rw =>
        if firstParent then rw.setFirstParent(true)
        val fromOid = ObjectId.fromString(from.value)
        rw.markStart(rw.parseCommit(fromOid))
        until.foreach: u =>
          rw.markUninteresting(rw.parseCommit(ObjectId.fromString(u.value)))
        val builder = IArray.newBuilder[RawCommit]
        // Walk loop: rw.next() returns the null sentinel at end-of-walk. The raw check avoids an Option allocation per
        // commit in this hot iteration and lets explicit-nulls flow-type `c` as non-null inside the body.
        var c = rw.next()
        while c != null do
          builder += toRawCommit(c)
          c = rw.next()
        Right(builder.result())
    catch
      case e: MissingObjectException       => Left(GitError.ObjectNotFound(e.getObjectId.unsafe.name))
      case e: IncorrectObjectTypeException => Left(GitError.BackendFailure(e.getMessage.unsafe))
      case e: java.io.IOException          => Left(GitError.BackendFailure(e.getMessage.unsafe))

  def loadCommit(sha: CommitSha): Either[GitError, RawCommit] =
    try
      Using.resource(new RevWalk(repo)): rw =>
        val oid = ObjectId.fromString(sha.value)
        val commit = rw.parseCommit(oid)
        Right(toRawCommit(commit))
    catch
      case e: MissingObjectException       => Left(GitError.ObjectNotFound(e.getObjectId.unsafe.name))
      case e: IncorrectObjectTypeException => Left(GitError.BackendFailure(e.getMessage.unsafe))
      case e: java.io.IOException          => Left(GitError.BackendFailure(e.getMessage.unsafe))

  def loadTagger(name: String): Either[GitError, Long] =
    try
      Using.resource(new RevWalk(repo)): rw =>
        repo
          .exactRef(Constants.R_TAGS + name)
          .option
          .flatMap(ref => ref.getObjectId.option)
          .toRight(GitError.ObjectNotFound(name))
          .map(oid => rw.parseTag(oid).getTaggerIdent.unsafe.getWhenAsInstant.unsafe.getEpochSecond)
    catch
      case e: MissingObjectException       => Left(GitError.ObjectNotFound(e.getObjectId.unsafe.name))
      case e: IncorrectObjectTypeException => Left(GitError.BackendFailure(e.getMessage.unsafe))
      case e: java.io.IOException          => Left(GitError.BackendFailure(e.getMessage.unsafe))

  def signingKey: Either[GitError, Option[String]] =
    Right(Option(GpgConfig(repo.getConfig).getSigningKey).map(_.unsafe).filter(_.nonEmpty))

  def defaultSignature: Either[GitError, AuthorSignature] =
    val user = repo.getConfig.get(UserConfig.KEY).unsafe
    val now = java.time.Instant.now().unsafe
    val offsetMinutes = java.time.ZoneId.systemDefault().unsafe.getRules.unsafe.getOffset(now).unsafe.getTotalSeconds / 60
    Right(AuthorSignature(user.getCommitterName.unsafe, user.getCommitterEmail.unsafe, now.getEpochSecond, offsetMinutes))

  def createCommit(
    message: String,
    author: AuthorSignature,
    sign: Boolean
  ): Either[GitError, CommitSha] =
    if sign then signedCommit(message, author)
    else
      try
        Using.resource(new JGit(repo)): git =>
          val ident = personIdent(author)
          val commit = git
            .commit()
            .setAllowEmpty(true)
            .setMessage(message)
            .setAuthor(ident)
            .setCommitter(ident)
            .setSign(java.lang.Boolean.FALSE)
            .call()
            .unsafe
          Right(CommitSha(commit.getId.unsafe.name))
      catch
        case e: org.eclipse.jgit.api.errors.GitAPIException => Left(GitError.BackendFailure(e.getMessage.unsafe))
        case e: java.io.IOException                         => Left(GitError.BackendFailure(e.getMessage.unsafe))

  def createTag(
    name: String,
    target: CommitSha,
    message: String,
    tagger: AuthorSignature,
    sign: Boolean
  ): Either[GitError, Unit] =
    if sign then signedTag(name, target, message, tagger)
    else
      try
        Using.resource(new RevWalk(repo)): rw =>
          val obj = rw.parseCommit(ObjectId.fromString(target.value))
          Using.resource(new JGit(repo)): git =>
            git
              .tag()
              .setName(name)
              .setObjectId(obj)
              .setAnnotated(true)
              .setMessage(message)
              .setTagger(personIdent(tagger))
              .setSigned(false)
              .call(): Unit
            Right(())
      catch
        case e: MissingObjectException                      => Left(GitError.ObjectNotFound(e.getObjectId.unsafe.name))
        case e: org.eclipse.jgit.api.errors.GitAPIException => Left(GitError.BackendFailure(e.getMessage.unsafe))
        case e: java.io.IOException                         => Left(GitError.BackendFailure(e.getMessage.unsafe))

  // Signed empty commit on HEAD: build the unsigned commit, sign its canonical bytes via the shared GpgSigner, embed the
  // signature as the gpgsig header, write the object, and fast-forward the current branch to it.
  private def signedCommit(message: String, author: AuthorSignature): Either[GitError, CommitSha] =
    signingKey.flatMap:
      case None      => Left(GitError.SigningFailure("signing requested but user.signingkey is not configured"))
      case Some(key) =>
        try
          repo
            .resolve(Constants.HEAD)
            .either[GitError](GitError.BackendFailure("cannot create a signed commit on an unborn HEAD"))
            .flatMap: headId =>
              Using.resource(new RevWalk(repo)): rw =>
                val parent = rw.parseCommit(headId)
                val ident = personIdent(author)
                val builder = new CommitBuilder()
                builder.setTreeId(parent.getTree)
                builder.setParentId(parent)
                builder.setAuthor(ident)
                builder.setCommitter(ident)
                builder.setMessage(message)
                GpgSigner
                  .sign(builder.build().unsafe, key)
                  .flatMap: signature =>
                    builder.setGpgSignature(new GpgSignature(signature.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    Using.resource(repo.newObjectInserter().unsafe): inserter =>
                      val commitId = inserter.insert(Constants.OBJ_COMMIT, builder.build()).unsafe
                      inserter.flush()
                      val update = repo.updateRef(Constants.HEAD).unsafe
                      update.setNewObjectId(commitId)
                      update.setExpectedOldObjectId(parent.getId)
                      update.setRefLogMessage("commit (signed): " + firstLine(message), false)
                      refUpdated(update.update().unsafe).map(_ => CommitSha(commitId.name))
        catch
          case e: MissingObjectException => Left(GitError.ObjectNotFound(e.getObjectId.unsafe.name))
          case e: java.io.IOException    => Left(GitError.BackendFailure(e.getMessage.unsafe))

  // Signed annotated tag: build the unsigned tag, sign its canonical bytes, append the signature, write the object, and
  // create refs/tags/<name>. JGit requires the message to end with a newline when signing so the signature verifies.
  private def signedTag(name: String, target: CommitSha, message: String, tagger: AuthorSignature): Either[GitError, Unit] =
    signingKey.flatMap:
      case None      => Left(GitError.SigningFailure("signing requested but user.signingkey is not configured"))
      case Some(key) =>
        try
          Using.resource(new RevWalk(repo)): rw =>
            val pointee = rw.parseCommit(ObjectId.fromString(target.value))
            val builder = new TagBuilder()
            builder.setObjectId(pointee)
            builder.setTag(name)
            builder.setTagger(personIdent(tagger))
            builder.setMessage(if message.endsWith("\n") then message else message + "\n")
            GpgSigner
              .sign(builder.build().unsafe, key)
              .flatMap: signature =>
                builder.setGpgSignature(new GpgSignature(signature.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                Using.resource(repo.newObjectInserter().unsafe): inserter =>
                  val tagId = inserter.insert(Constants.OBJ_TAG, builder.build()).unsafe
                  inserter.flush()
                  val update = repo.updateRef(Constants.R_TAGS + name).unsafe
                  update.setNewObjectId(tagId)
                  update.setRefLogMessage("tag (signed): " + name, false)
                  refUpdated(update.update().unsafe)
        catch
          case e: MissingObjectException => Left(GitError.ObjectNotFound(e.getObjectId.unsafe.name))
          case e: java.io.IOException    => Left(GitError.BackendFailure(e.getMessage.unsafe))

  private def refUpdated(result: RefUpdate.Result): Either[GitError, Unit] =
    // Java enum singletons: reference equality, avoiding a CanEqual instance under strict equality.
    if result.eq(RefUpdate.Result.NEW) || result.eq(RefUpdate.Result.FAST_FORWARD) || result.eq(RefUpdate.Result.FORCED) then Right(())
    else Left(GitError.BackendFailure(s"reference update rejected: $result"))

  private def firstLine(s: String): String = s.takeWhile(_ != '\n')

  def close(): Unit =
    if !closed then
      closed = true
      try repo.close()
      catch case scala.util.control.NonFatal(_) => ()

  private def toRawCommit(c: RevCommit): RawCommit =
    val parentIds = IArray.from(c.getParents.unsafe.map(p => CommitSha(p.unsafe.getId.name)))
    RawCommit(
      id = CommitSha(c.getId.name),
      message = c.getFullMessage.unsafe,
      parentIds = parentIds,
      commitTime = c.getCommitTime.toLong
    )

  private def personIdent(a: AuthorSignature): PersonIdent =
    val when = java.time.Instant.ofEpochSecond(a.whenEpochSeconds).unsafe
    val zone = java.time.ZoneOffset.ofTotalSeconds(a.offsetMinutes * 60).unsafe
    new PersonIdent(a.name, a.email, when, zone)
end JvmGitRepository

object JvmGitRepository:

  /** Opens a Git repository at the given path. */
  def open(path: String): Either[GitError, JvmGitRepository] =
    val file = java.io.File(path)
    val gitDir = java.io.File(file, ".git")
    if !gitDir.isDirectory && !gitDir.isFile then Left(GitError.RepositoryNotFound(path))
    else
      try
        val builder = FileRepositoryBuilder()
        builder.setWorkTree(file)
        builder.setGitDir(gitDir)
        builder.setMustExist(true)
        val repo = builder.build()
        Right(new JvmGitRepository(repo.unsafe))
      catch
        case _: RepositoryNotFoundException => Left(GitError.RepositoryNotFound(path))
        case e: java.io.IOException         => Left(GitError.BackendFailure(e.getMessage.unsafe))
// scalafix:on
