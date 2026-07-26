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

// git_buf { char *ptr; size_t reserved; size_t size; }; libgit2 owns the allocation until git_buf_dispose.
private[native] type GitBuf = CStruct3[CString, CSize, CSize]

// git_time { git_time_t time; int offset; char sign; }, time being seconds since the Unix epoch.
private[native] type GitTime = CStruct3[CLongLong, CInt, CChar]

// git_signature { char *name; char *email; git_time when; }.
private[native] type GitSignature = CStruct3[CString, CString, GitTime]

// Separate from LibGit2 because the scala-native FFI macro rejects `inline val` inside an `@extern` object.
private[native] object LibGit2Constants:
  inline val GIT_OK = 0
  inline val GIT_ENOTFOUND = -3
  inline val GIT_EAMBIGUOUS = -5
  inline val GIT_EUNBORNBRANCH = -9
  inline val GIT_ITEROVER = -31

  inline val GIT_OBJECT_COMMIT = 1
  inline val GIT_OBJECT_TAG = 4

  inline val GIT_REPOSITORY_OPEN_NO_SEARCH = 1

  // Position within git_libgit2_opt_t, which libgit2 declares without explicit values.
  inline val GIT_OPT_SET_OWNER_VALIDATION = 36

  inline def GIT_SORT_TIME = 2.toUInt

  inline val GIT_OID_SHA1_SIZE = 20
  inline val GIT_OID_SHA1_HEXSIZE = 40

// Handles stay untyped as Ptr[Byte]: the call site knows which libgit2 object it holds and which returns may be null.
@extern
private[native] object LibGit2:

  // Library lifecycle
  def git_libgit2_init(): CInt = extern
  def git_libgit2_shutdown(): CInt = extern
  def git_libgit2_opts(option: CInt, args: Any*): CInt = extern

  // Repository
  def git_repository_open_ext(out: Ptr[Ptr[Byte]], path: CString, flags: CUnsignedInt, ceilingDirs: CString): CInt = extern
  def git_repository_free(repo: Ptr[Byte]): Unit = extern
  def git_repository_head(out: Ptr[Ptr[Byte]], repo: Ptr[Byte]): CInt = extern
  def git_repository_head_detached(repo: Ptr[Byte]): CInt = extern
  def git_repository_head_unborn(repo: Ptr[Byte]): CInt = extern
  def git_repository_is_bare(repo: Ptr[Byte]): CInt = extern
  def git_repository_path(repo: Ptr[Byte]): CString = extern
  def git_repository_workdir(repo: Ptr[Byte]): CString = extern

  // Objects
  def git_object_id(obj: Ptr[Byte]): Ptr[Byte] = extern
  def git_object_free(obj: Ptr[Byte]): Unit = extern

  // Revparse
  def git_revparse_single(out: Ptr[Ptr[Byte]], repo: Ptr[Byte], spec: CString): CInt = extern

  // References
  def git_reference_iterator_glob_new(out: Ptr[Ptr[Byte]], repo: Ptr[Byte], glob: CString): CInt = extern
  def git_reference_next(out: Ptr[Ptr[Byte]], iter: Ptr[Byte]): CInt = extern
  def git_reference_iterator_free(iter: Ptr[Byte]): Unit = extern
  def git_reference_shorthand(ref: Ptr[Byte]): CString = extern
  def git_reference_lookup(out: Ptr[Ptr[Byte]], repo: Ptr[Byte], name: CString): CInt = extern
  def git_reference_peel(out: Ptr[Ptr[Byte]], ref: Ptr[Byte], targetType: CInt): CInt = extern
  def git_reference_free(ref: Ptr[Byte]): Unit = extern
  def git_reference_set_target(out: Ptr[Ptr[Byte]], ref: Ptr[Byte], id: Ptr[Byte], logMessage: CString): CInt = extern

  def git_commit_lookup(out: Ptr[Ptr[Byte]], repo: Ptr[Byte], oid: Ptr[Byte]): CInt = extern
  def git_commit_parentcount(commit: Ptr[Byte]): CUnsignedInt = extern
  def git_commit_parent_id(commit: Ptr[Byte], n: CUnsignedInt): Ptr[Byte] = extern
  def git_commit_message(commit: Ptr[Byte]): CString = extern
  def git_commit_time(commit: Ptr[Byte]): CLongLong = extern
  def git_commit_free(commit: Ptr[Byte]): Unit = extern

  // Revwalk
  def git_revwalk_new(out: Ptr[Ptr[Byte]], repo: Ptr[Byte]): CInt = extern
  def git_revwalk_push(walk: Ptr[Byte], oid: Ptr[Byte]): CInt = extern
  def git_revwalk_hide(walk: Ptr[Byte], oid: Ptr[Byte]): CInt = extern
  def git_revwalk_simplify_first_parent(walk: Ptr[Byte]): Unit = extern
  def git_revwalk_sorting(walk: Ptr[Byte], sortMode: CUnsignedInt): CInt = extern
  def git_revwalk_next(out: Ptr[Byte], walk: Ptr[Byte]): CInt = extern
  def git_revwalk_free(walk: Ptr[Byte]): Unit = extern

  // Graph
  def git_graph_descendant_of(repo: Ptr[Byte], commit: Ptr[Byte], ancestor: Ptr[Byte]): CInt = extern

  // Signatures
  def git_signature_new(out: Ptr[Ptr[Byte]], name: CString, email: CString, time: CLongLong, offset: CInt): CInt = extern
  def git_signature_free(sig: Ptr[Byte]): Unit = extern

  // Commit and tag creation
  def git_commit_tree(out: Ptr[Ptr[Byte]], commit: Ptr[Byte]): CInt = extern
  def git_tree_free(tree: Ptr[Byte]): Unit = extern
  def git_commit_create(
    id: Ptr[Byte],
    repo: Ptr[Byte],
    updateRef: CString,
    author: Ptr[Byte],
    committer: Ptr[Byte],
    messageEncoding: CString,
    message: CString,
    tree: Ptr[Byte],
    parentCount: CSize,
    parents: Ptr[Ptr[Byte]]): CInt = extern
  def git_tag_create(
    oid: Ptr[Byte],
    repo: Ptr[Byte],
    tagName: CString,
    target: Ptr[Byte],
    tagger: Ptr[Byte],
    message: CString,
    force: CInt): CInt = extern
  def git_tag_create_from_buffer(oid: Ptr[Byte], repo: Ptr[Byte], buffer: CString, force: CInt): CInt = extern
  def git_tag_tagger(tag: Ptr[Byte]): Ptr[GitSignature] = extern

  // Signing primitives. git_commit_create_with_signature writes the object but advances no reference; the branch has
  // to be moved separately.
  def git_commit_create_buffer(
    out: Ptr[GitBuf],
    repo: Ptr[Byte],
    author: Ptr[Byte],
    committer: Ptr[Byte],
    messageEncoding: CString,
    message: CString,
    tree: Ptr[Byte],
    parentCount: CSize,
    parents: Ptr[Ptr[Byte]]): CInt = extern
  def git_commit_create_with_signature(
    id: Ptr[Byte],
    repo: Ptr[Byte],
    commitContent: CString,
    signature: CString,
    signatureField: CString): CInt = extern
  def git_buf_dispose(buf: Ptr[GitBuf]): Unit = extern

  // Config. git_config_get_string errors on a live config and, on a snapshot, borrows that snapshot's memory.
  def git_repository_config_snapshot(out: Ptr[Ptr[Byte]], repo: Ptr[Byte]): CInt = extern
  def git_config_get_string(out: Ptr[CString], cfg: Ptr[Byte], name: CString): CInt = extern
  def git_config_free(cfg: Ptr[Byte]): Unit = extern

  // Errors
  def git_error_last(): Ptr[Byte] = extern

  // git_status_options has to be allocated on the C-side stack: heap-allocating it across the FFI boundary segfaults
  // inside git_diff_index_to_workdir on musl.
  @name("version_resolution_git_workdir_dirty_count")
  def git_workdir_dirty_count(repo: Ptr[Byte]): CInt = extern

  // Reading git_error { char *message; int klass; } through a shim keeps the struct shape out of the Scala side.
  @name("version_resolution_git_error_message")
  def git_error_message(err: Ptr[Byte]): CString = extern

end LibGit2
