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

import munit.FunSuite

import java.io.IOException
import java.nio.file.Files

final class FilesystemSuite extends FunSuite:

  test("ensureDirectory creates a directory and its missing parents"):
    val root = Files.createTempDirectory("version-testkit-ensure-")
    try
      val nested = root.resolve("a").resolve("b")
      assertEquals(Filesystem.ensureDirectory(nested), nested)
      assert(Files.isDirectory(nested))
    finally Filesystem.removeRecursive(root)

  test("a directory that cannot be created is reported with what stands at it and at its parent"):
    val root = Files.createTempDirectory("version-testkit-ensure-")
    try
      val occupied = root.resolve("file")
      Files.writeString(occupied, "not a directory"): Unit
      val target = occupied.resolve("child")
      val error = intercept[IOException](Filesystem.ensureDirectory(target))
      assert(error.getMessage.contains(s"cannot create directory $target"), clue(error.getMessage))
      assert(error.getMessage.contains("exists=false, directory=false"), clue(error.getMessage))
      assert(error.getMessage.contains(s"parent $occupied exists=true, directory=false"), clue(error.getMessage))
    finally Filesystem.removeRecursive(root)

end FilesystemSuite
