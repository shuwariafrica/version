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

import org.eclipse.jgit.api.Git as JGit
import org.eclipse.jgit.errors.AmbiguousObjectException
import org.eclipse.jgit.errors.IncorrectObjectTypeException
import org.eclipse.jgit.errors.MissingObjectException
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.errors.RevisionSyntaxException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder

import scala.annotation.threadUnsafe
import scala.util.Using
import scala.util.boundary
import scala.util.boundary.break

import version.resolution.GitError
import version.resolution.GitRepository
import version.resolution.domain.*

// scalafix:off
/** JGit-backed [[GitRepository]] implementation for the JVM platform. */
final class JvmGitRepository private (repo: Repository) extends GitRepository:

  @threadUnsafe private var closed: Boolean = false

  def head: Either[GitError, Option[CommitSha]] =
    try
      val ref = repo.exactRef(Constants.HEAD)
      if ref == null then Right(None)
      else
        val leaf = ref.getLeaf
        val oid = leaf.getObjectId
        if oid == null then Right(None)
        else Right(Some(CommitSha(oid.name)))
    catch case e: java.io.IOException => Left(GitError.BackendFailure(e.getMessage.nn))

  def resolve(rev: String): Either[GitError, CommitSha] =
    try
      val oid = repo.resolve(s"$rev^{commit}")
      if oid == null then Left(GitError.RevisionNotFound(rev))
      else Right(CommitSha(oid.name))
    catch
      case e: AmbiguousObjectException => Left(GitError.AmbiguousRevision(rev))
      case _: RevisionSyntaxException  => Left(GitError.RevisionNotFound(rev))
      case e: java.io.IOException      => Left(GitError.BackendFailure(e.getMessage.nn))

  def branch: Either[GitError, Option[String]] =
    try
      val ref = repo.exactRef(Constants.HEAD)
      if ref == null then Right(None)
      else if ref.isSymbolic then Right(Some(Repository.shortenRefName(ref.getTarget.getName)))
      else Right(None)
    catch case e: java.io.IOException => Left(GitError.BackendFailure(e.getMessage.nn))

  def isBare: Boolean = repo.isBare

  def clean: Either[GitError, Boolean] =
    if isBare then Right(true)
    else
      try
        Using.resource(new JGit(repo)): git =>
          Right(git.status().call().isClean)
      catch
        case e: org.eclipse.jgit.api.errors.GitAPIException =>
          Left(GitError.BackendFailure(e.getMessage.nn))
        case e: java.io.IOException =>
          Left(GitError.BackendFailure(e.getMessage.nn))

  def tags: Either[GitError, IArray[RawTag]] =
    try
      val refDb = repo.getRefDatabase
      val refs = refDb.getRefsByPrefix(Constants.R_TAGS)
      val builder = IArray.newBuilder[RawTag]
      val iter = refs.iterator
      while iter.hasNext do
        val ref = iter.next().nn
        val peeled = refDb.peel(ref)
        val peeledOid = peeled.getPeeledObjectId
        val (commitOid, kind) =
          if peeledOid != null then (peeledOid, TagKind.Annotated)
          else (peeled.getObjectId, TagKind.Lightweight)
        if commitOid != null then
          val shortName = ref.getName.nn.stripPrefix(Constants.R_TAGS)
          builder += RawTag(shortName, CommitSha(commitOid.name), kind)
      Right(builder.result())
    catch case e: java.io.IOException => Left(GitError.BackendFailure(e.getMessage.nn))

  def isAncestorOf(ancestor: CommitSha, commit: CommitSha): Either[GitError, Boolean] =
    if ancestor.value == commit.value then Right(true)
    else
      try
        Using.resource(new RevWalk(repo)): rw =>
          val base = rw.parseCommit(ObjectId.fromString(ancestor.value))
          val tip = rw.parseCommit(ObjectId.fromString(commit.value))
          Right(rw.isMergedInto(base, tip))
      catch
        case e: MissingObjectException       => Left(GitError.ObjectNotFound(e.getObjectId.nn.name))
        case e: IncorrectObjectTypeException => Left(GitError.BackendFailure(e.getMessage.nn))
        case e: java.io.IOException          => Left(GitError.BackendFailure(e.getMessage.nn))

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
        case e: MissingObjectException       => Left(GitError.ObjectNotFound(e.getObjectId.nn.name))
        case e: IncorrectObjectTypeException => Left(GitError.BackendFailure(e.getMessage.nn))
        case e: java.io.IOException          => Left(GitError.BackendFailure(e.getMessage.nn))

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
        var c = rw.next()
        while c != null do
          builder += toRawCommit(c)
          c = rw.next()
        Right(builder.result())
    catch
      case e: MissingObjectException       => Left(GitError.ObjectNotFound(e.getObjectId.nn.name))
      case e: IncorrectObjectTypeException => Left(GitError.BackendFailure(e.getMessage.nn))
      case e: java.io.IOException          => Left(GitError.BackendFailure(e.getMessage.nn))

  def loadCommit(sha: CommitSha): Either[GitError, RawCommit] =
    try
      Using.resource(new RevWalk(repo)): rw =>
        val oid = ObjectId.fromString(sha.value)
        val commit = rw.parseCommit(oid)
        Right(toRawCommit(commit))
    catch
      case e: MissingObjectException       => Left(GitError.ObjectNotFound(e.getObjectId.nn.name))
      case e: IncorrectObjectTypeException => Left(GitError.BackendFailure(e.getMessage.nn))
      case e: java.io.IOException          => Left(GitError.BackendFailure(e.getMessage.nn))

  def abbreviate(id: CommitSha, length: Int): Either[GitError, String] =
    try
      val oid = ObjectId.fromString(id.value)
      Right(oid.abbreviate(length).name)
    catch case e: java.io.IOException => Left(GitError.BackendFailure(e.getMessage.nn))

  def close(): Unit =
    if !closed then
      closed = true
      try repo.close()
      catch case scala.util.control.NonFatal(_) => ()

  private def toRawCommit(c: RevCommit): RawCommit =
    val parentIds = IArray.from(c.getParents.nn.map(p => CommitSha(p.nn.getId.name)))
    RawCommit(
      id = CommitSha(c.getId.name),
      message = c.getFullMessage.nn,
      parentIds = parentIds,
      commitTime = c.getCommitTime.toLong
    )
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
        Right(new JvmGitRepository(repo.nn))
      catch
        case _: RepositoryNotFoundException => Left(GitError.RepositoryNotFound(path))
        case e: java.io.IOException         => Left(GitError.BackendFailure(e.getMessage.nn))
// scalafix:on
