import sbt.*
import sbt.Keys.*
import sbtunidoc.ScalaUnidocPlugin.autoImport.*
import sbtunidoc.BaseUnidocPlugin.autoImport.*
import mdoc.MdocPlugin
import mdoc.MdocPlugin.autoImport.*
import sbt.plugins.JvmPlugin
import sbtunidoc.ScalaUnidocPlugin

/** AutoPlugin assembling the `version` documentation site: mdoc preprocessing of the prose pages and the repo-root
  * README, Scaladoc/Unidoc generation, and the final static-site assembly under `target/site`.
  */
object VersionUnidocPlugin extends AutoPlugin:

  override def requires: Plugins = JvmPlugin && MdocPlugin && ScalaUnidocPlugin
  override def trigger: PluginTrigger = noTrigger

  object autoImport:
    val documentationSourceLinks = settingKey[String]("Source link configuration for Scaladoc")
    val documentationFooter = settingKey[String]("Footer text for documentation")
    val generateUnidoc = taskKey[File]("Generate unified API documentation with static site")
    val preprocessDocs = taskKey[File]("Run mdoc over the prose pages and the README source; copy site assets")
    val scalaNativeVersion = settingKey[String]("Version of Scala Native used to build the project")

  private val renderedReadme = Def.setting((ThisProject / target).value / "readme" / "README.md")
  private val documentationSite = Def.setting((ThisProject / target).value / "site")

  import autoImport.*

  // Render the `_docs/` prose pages via mdoc and copy non-markdown assets
  // (sidebar.yml, rootdoc.md, _assets, _layouts) into the dottydoc siteroot.
  private val processSite: Def.Initialize[Task[File]] = Def.task {
    val log = streams.value.log
    val _ = (Compile / mdoc).toTask("").value

    val docsRoot = (ThisProject / baseDirectory).value
    val siteRoot = mdocOut.value.getParentFile

    val filesToCopy = Seq(
      docsRoot / "sidebar.yml" -> siteRoot / "sidebar.yml",
      docsRoot / "rootdoc.md" -> siteRoot / "rootdoc.md"
    )
    val dirsToCopy = Seq(
      docsRoot / "_assets" -> siteRoot / "_assets",
      docsRoot / "_layouts" -> siteRoot / "_layouts"
    )
    filesToCopy.foreach: (src, dest) =>
      if src.exists() then
        IO.copyFile(src, dest)
        log.info(s"Copied ${src.getName} -> ${dest.getAbsolutePath}")
    dirsToCopy.foreach: (src, dest) =>
      if src.exists() then
        IO.copyDirectory(src, dest)
        log.info(s"Copied ${src.getName}/ -> ${dest.getAbsolutePath}")

    siteRoot
  }

  // Render the README source under the docs project to `renderedReadme.value`. The repo-root
  // README is the rendered output (committed by the release workflow); keeping the source
  // separate is what stops rendering replacing its own placeholders on the next release.
  // Output sits outside the dottydoc siteroot so it is never picked up as a doc page.
  // Def.taskDyn lets the --in/--out args compose from task-time settings before mdoc's
  // InputTask is constructed.
  private val processReadme: Def.Initialize[Task[File]] = Def.taskDyn {
    val sourceReadme = (ThisProject / baseDirectory).value / "README.md"
    val readmeOut = renderedReadme.value
    val args = s" --in ${sourceReadme.getAbsolutePath} --out ${readmeOut.getAbsolutePath}"
    Def.task {
      val _ = (Compile / mdoc).toTask(args).value
      readmeOut
    }
  }

  override def projectSettings: Seq[Setting[?]] = Seq(
    documentationSourceLinks := {
      val rev = sys.env.getOrElse("GIT_REVISION", "main")
      s"github://shuwariafrica/version/$rev"
    },
    documentationFooter := s"`version` - v${version.value}",

    mdocIn := (ThisProject / baseDirectory).value / "_docs",
    mdocOut := target.value / "docs" / "_docs",
    mdocVariables := Map(
      "VERSION" -> version.value,
      "SCALA3_VERSION" -> scalaVersion.value,
      "SCALANATIVE_VERSION" -> scalaNativeVersion.value,
      "JDK_VERSION" -> {
        val version = java.lang.Runtime.version()
        s"${version.feature()}.${version.interim()}.${version.update()}"
      }
    ),
    mdocExtraArguments := Seq("--no-link-hygiene"),

    preprocessDocs := Def.uncached {
      val site = processSite.value
      val _ = processReadme.value
      site
    },

    Compile / unidoc := (Compile / unidoc).dependsOn(preprocessDocs).value,

    Compile / doc / scalacOptions ++= Def.uncached {
      val processedDocsDir = preprocessDocs.value
      val rootContent = processedDocsDir / "rootdoc.md"
      val logo = processedDocsDir / "_assets" / "media" / "logo.svg"

      val baseOptions = Seq(
        "-project",
        "`version`",
        "-project-version",
        version.value,
        "-project-url",
        "https://version.shuwari.africa",
        "-siteroot",
        processedDocsDir.getAbsolutePath,
        "-author",
        "-groups",
        "-Ygenerate-inkuire",
        "-project-footer",
        documentationFooter.value,
        "-social-links",
        "github::https://github.com/shuwariafrica/version",
        "-source-links",
        documentationSourceLinks.value
      )

      val rootContentOption =
        if rootContent.exists() then Seq("-doc-root-content", rootContent.getAbsolutePath)
        else Seq.empty
      val logoOption =
        if logo.exists() then Seq("-project-logo", logo.getAbsolutePath)
        else Seq.empty

      baseOptions ++ rootContentOption ++ logoOption
    },

    generateUnidoc := Def.uncached {
      val log = streams.value.log
      val docDirs = (Compile / unidoc).value
      val mainDocDir = docDirs.headOption.getOrElse(target.value / "unidoc")
      val siteDir = documentationSite.value
      val readmeOut = renderedReadme.value
      if siteDir.exists() then IO.delete(siteDir)
      IO.copyDirectory(mainDocDir, siteDir)
      log.info(s"Documentation site assembled at ${siteDir.getAbsolutePath}")

      // Emit produced paths to $GITHUB_OUTPUT so the release workflow does
      // not need to know sbt's per-axis target layout.
      sys.env.get("GITHUB_OUTPUT").foreach { ghOutput =>
        val outputs = Seq(
          s"site_dir=${siteDir.getAbsolutePath}",
          s"readme_path=${readmeOut.getAbsolutePath}",
          ""
        ).mkString("\n")
        IO.append(new File(ghOutput), outputs)
      }

      siteDir
    }
  )

end VersionUnidocPlugin
