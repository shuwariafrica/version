/*
 * Copyright (c) 2023 Shuwari Africa Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import sbt.*
import sbt.Keys.*
import sbtunidoc.ScalaUnidocPlugin.autoImport.*
import sbtunidoc.BaseUnidocPlugin.autoImport.*
import mdoc.MdocPlugin
import mdoc.MdocPlugin.autoImport.*
import sbt.plugins.JvmPlugin
import sbtunidoc.ScalaUnidocPlugin

/** Plugin for managing Scaladoc/Unidoc settings for the `version` project.
  *
  * Must be manually enabled on the root project to keep documentation settings organised.
  * This plugin requires MdocPlugin because it uses mdoc to preprocess documentation
  * files before Scaladoc generation, enabling dynamic variable substitution.
  */
object VersionUnidocPlugin extends AutoPlugin {

  override def requires: Plugins = JvmPlugin && MdocPlugin && ScalaUnidocPlugin
  override def trigger: PluginTrigger = noTrigger // Manual activation required

  object autoImport {
    // Custom keys for documentation
    val documentationSourceLinks = settingKey[String]("Source link configuration for Scaladoc")
    val documentationFooter = settingKey[String]("Footer text for documentation")
    val generateUnidoc = taskKey[File]("Generate unified API documentation with static site")
    val preprocessDocs = taskKey[File]("Preprocess documentation with mdoc and copy assets")

    // Version keys for injection into documentation via mdoc
    val scalaJsVersion = settingKey[String]("Scala.js version used in the project")
    val scalaNativeVersion = settingKey[String]("Scala Native version used in the project")
  }

  import autoImport.*

  override def projectSettings: Seq[Setting[?]] = Seq(
    // Default documentation settings
    documentationSourceLinks := {
      val rev = sys.env.getOrElse("GIT_REVISION", "main")
      s"github://shuwariafrica/version/$rev"
    },
    documentationFooter := s"`version` - v${version.value}",

    // Configure mdoc to preprocess documentation with version variables
    mdocIn := file("docs") / "_docs",
    mdocOut := target.value / "mdoc-processed" / "_docs",
    mdocVariables := Map(
      "SCALA3_VERSION" -> scalaVersion.value,
      "SCALAJS_VERSION" -> pluginVersions.value.scalaJS,
      "SCALANATIVE_VERSION" -> pluginVersions.value.scalaNative,
      "JDK_VERSION" -> {
        val version = java.lang.Runtime.version()
        s"${version.feature()}.${version.interim()}.${version.update()}"
      },
      "VERSION" -> version.value
    ),

    // Suppress link hygiene warnings for API references mdoc can't validate
    mdocExtraArguments := Seq("--no-link-hygiene"),

    // Task to run mdoc and copy non-markdown assets
    preprocessDocs := {
      val log = streams.value.log

      // First run mdoc to process markdown files (mdoc is an InputTask)
      val _ = (Compile / mdoc).toTask("").value

      val docsRoot = file("docs")
      val mdocOutputRoot = mdocOut.value.getParentFile // target/mdoc-processed

      // Copy sidebar.yml, rootdoc.md, _assets, _layouts if they exist
      val filesToCopy = List(
        docsRoot / "sidebar.yml" -> mdocOutputRoot / "sidebar.yml",
        docsRoot / "rootdoc.md" -> mdocOutputRoot / "rootdoc.md"
      )

      val dirsToCopy = List(
        docsRoot / "_assets" -> mdocOutputRoot / "_assets",
        docsRoot / "_layouts" -> mdocOutputRoot / "_layouts"
      )

      // Copy files
      filesToCopy.foreach { case (src, dest) =>
        if (src.exists()) {
          IO.copyFile(src, dest)
          log.info(s"Copied ${src.getName} to mdoc output")
        }
      }

      // Copy directories
      dirsToCopy.foreach { case (src, dest) =>
        if (src.exists()) {
          IO.copyDirectory(src, dest)
          log.info(s"Copied ${src.getName}/ to mdoc output")
        }
      }

      mdocOutputRoot
    },

    // Ensure unidoc depends on preprocessDocs for complete pipeline
    Compile / unidoc := (Compile / unidoc).dependsOn(preprocessDocs).value,

    // Enhanced Scaladoc options - these will be picked up by ScalaUnidoc
    Compile / doc / scalacOptions ++= {
      // CRITICAL: Run mdoc (with asset copying) BEFORE generating Scaladoc
      val processedDocsDir = preprocessDocs.value

      val siteRoot = processedDocsDir.getAbsolutePath
      val projectUrl = "https://version.shuwari.africa"
      val socialLinks = Seq("github" -> "https://github.com/shuwariafrica/version")
      val sourceLinks = documentationSourceLinks.value
      // rootdoc.md is now in the mdoc output directory
      val rootContent = processedDocsDir / "rootdoc.md"
      // logo is in the copied _assets directory
      val logo = processedDocsDir / "_assets" / "media" / "logo.svg"
      val footer = documentationFooter.value

      val baseOptions = Seq(
        "-project",
        "`version`",
        "-project-version",
        version.value,
        "-project-url",
        projectUrl,
        "-siteroot",
        siteRoot,
        "-author",
        "-groups",
        // Enable Inkuire type-based search engine
        "-Ygenerate-inkuire"
      )

      val rootContentOption = if (rootContent.exists()) {
        Seq("-doc-root-content", rootContent.getAbsolutePath)
      } else Seq.empty

      val logoOption = if (logo.exists()) {
        Seq("-project-logo", logo.getAbsolutePath)
      } else Seq.empty

      val footerOption = Seq("-project-footer", footer)

      val socialLinkOptions = socialLinks.flatMap { case (platform, url) =>
        Seq("-social-links", s"$platform::$url")
      }

      val sourceLinkOptions = Seq("-source-links", sourceLinks)

      baseOptions ++ rootContentOption ++ logoOption ++ footerOption ++
        socialLinkOptions ++ sourceLinkOptions
    },

    // Generate unified documentation task
    generateUnidoc := {
      val log = streams.value.log
      log.info("Generating unified API documentation with static site...")

      // This uses the ScalaUnidoc plugin
      val docDirs = (Compile / unidoc).value
      val mainDocDir = docDirs.headOption.getOrElse(target.value / "unidoc")

      // Copy to a stable (scala-version agnostic) site directory for publishing
      val siteDir = target.value / "site"
      if (siteDir.exists()) IO.delete(siteDir)
      IO.copyDirectory(mainDocDir, siteDir)
      log.info(s"Copied unified documentation to: ${siteDir.getAbsolutePath}")
      siteDir
    }
  )

  final private case class PluginVersions(scalaJS: String, scalaNative: String)

  private lazy val pluginVersions = Def.setting[PluginVersions] {
    val sourceFile = (LocalRootProject / baseDirectory).value / "project" / "plugins.sbt"
    val lines = IO.read(sourceFile)

    def findVersion(regex: scala.util.matching.Regex, pluginName: String): String =
      regex
        .findFirstMatchIn(lines)
        .map(_.group(1)) // Extract the first captured group (the version string)
        .getOrElse(sys.error(s"Could not find version for $pluginName in $sourceFile"))

    // Regex to find: addSbtPlugin("org.scala-js" % "sbt-scalajs" % "VERSION")
    val scalaJsRegex = """addSbtPlugin\("org\.scala-js"\s*%\s*"sbt-scalajs"\s*%\s*"([^"]+)"\)""".r

    // Regex to find: addSbtPlugin("org.scala-native" % "sbt-scala-native" % "VERSION")
    val scalaNativeRegex = """addSbtPlugin\("org\.scala-native"\s*%\s*"sbt-scala-native"\s*%\s*"([^"]+)"\)""".r

    PluginVersions(
      scalaJS = findVersion(scalaJsRegex, "sbt-scalajs"),
      scalaNative = findVersion(scalaNativeRegex, "sbt-scala-native")
    )
  }
}
