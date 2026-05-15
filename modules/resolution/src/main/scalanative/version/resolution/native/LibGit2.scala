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

/** libgit2 constants used by [[LibGit2]] and [[NativeGitRepository]].
  *
  * Held separately from the `@extern` object so the values can be `inline val` literal-typed constants; the
  * scala-native FFI macro does not allow `inline val` declarations inside an `@extern` object.
  */
private[native] object LibGit2Constants:
  inline val GIT_OK = 0
  inline val GIT_ENOTFOUND = -3
  inline val GIT_EAMBIGUOUS = -5
  inline val GIT_EUNBORNBRANCH = -9
  inline val GIT_ITEROVER = -31

  inline val GIT_OBJECT_COMMIT = 1
  inline val GIT_OBJECT_TAG = 4

  inline def GIT_SORT_TIME = 2.toUInt

  inline val GIT_OID_SHA1_SIZE = 20
  inline val GIT_OID_SHA1_HEXSIZE = 40

/** Low-level libgit2 FFI bindings.
  *
  * Direct C function bindings consumed by [[NativeGitRepository]] only. Pointer types are `Ptr[Byte]`; nominal
  * discrimination via opaque types is applied at the call site. Returned pointers from libgit2 are not nullable unless
  * the upstream documentation says otherwise; null handling lives at the call site.
  */
@extern
private[native] object LibGit2:

  // Library lifecycle
  def git_libgit2_init(): CInt = extern
  def git_libgit2_shutdown(): CInt = extern

  // Repository
  def git_repository_open_ext(out: Ptr[Ptr[Byte]], path: CString, flags: CUnsignedInt, ceilingDirs: CString): CInt = extern
  def git_repository_free(repo: Ptr[Byte]): Unit = extern
  def git_repository_head(out: Ptr[Ptr[Byte]], repo: Ptr[Byte]): CInt = extern
  def git_repository_head_detached(repo: Ptr[Byte]): CInt = extern
  def git_repository_head_unborn(repo: Ptr[Byte]): CInt = extern
  def git_repository_is_bare(repo: Ptr[Byte]): CInt = extern

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
  def git_reference_peel(out: Ptr[Ptr[Byte]], ref: Ptr[Byte], targetType: CInt): CInt = extern
  def git_reference_free(ref: Ptr[Byte]): Unit = extern

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

  // Errors
  def git_error_last(): Ptr[Byte] = extern

  // The status walk for the working-tree dirty count must allocate
  // git_status_options on the C-side stack; heap-allocating that struct
  // across the FFI boundary segfaults inside git_diff_index_to_workdir
  // on musl.
  @name("version_resolution_git_workdir_dirty_count")
  def git_workdir_dirty_count(repo: Ptr[Byte]): CInt = extern

  // git_error is { char* message; int klass; }; reading message field through
  // a C shim avoids declaring the struct shape on the Scala side.
  @name("version_resolution_git_error_message")
  def git_error_message(err: Ptr[Byte]): CString = extern

  // TODO(scala-native#4908): remove once the fix ships.
  @name("version_resolution_fix_main_thread_stack_limit")
  def fix_main_thread_stack_limit(): CInt = extern

end LibGit2
