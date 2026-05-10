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
package version.testkit

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

object Filesystem:

  def removeRecursive(path: Path): Unit =
    if Files.exists(path) then Files.walkFileTree(path, deletingVisitor): Unit

  private val deletingVisitor = new SimpleFileVisitor[Path]:
    override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult =
      Files.deleteIfExists(file): Unit
      FileVisitResult.CONTINUE

    override def postVisitDirectory(dir: Path, exc: IOException | Null): FileVisitResult =
      Files.deleteIfExists(dir): Unit
      FileVisitResult.CONTINUE

end Filesystem
