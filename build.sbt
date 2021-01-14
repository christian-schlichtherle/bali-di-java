/*
 * Copyright © 2020 Schlichtherle IT Services
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._

val ScalaTest: ModuleID = "org.scalatest" %% "scalatest" % "3.2.3"

inThisBuild(Seq(
  autoScalaLibrary := false,
  compileOrder := CompileOrder.JavaThenScala,
  crossPaths := false,
  fork := true,
  homepage := Some(url("https://bali.namespace.global/")),
  javacOptions := DefaultOptions.javac ++ Seq(Opts.compile.deprecation, "-source", "1.8", "-target", "1.8"),
  javacOptions in doc := DefaultOptions.javac ++ Seq("-source", "1.8"),
  licenses := Seq("Apache License, Version 2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
  logBuffered := false, // http://www.scalatest.org/user_guide/using_scalatest_with_sbt
  organization := "global.namespace.bali",
  organizationHomepage := Some(new URL("https://schlichtherle.de")),
  organizationName := "Schlichtherle IT Services",
  pomExtra := {
    <developers>
      <developer>
        <name>Christian Schlichtherle</name>
        <email>christian AT schlichtherle DOT de</email>
        <organization>Schlichtherle IT Services</organization>
        <timezone>2</timezone>
        <roles>
          <role>owner</role>
        </roles>
        <properties>
          <picUrl>http://www.gravatar.com/avatar/e2f69ddc944f8891566fc4b18518e4e6.png</picUrl>
        </properties>
      </developer>
    </developers>
      <issueManagement>
        <system>Github</system>
        <url>https://github.com/christian-schlichtherle/bali-di/issues</url>
      </issueManagement>
  },
  pomIncludeRepository := (_ => false),
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    Some(
      if (version(_ endsWith "-SNAPSHOT").value) {
        "snapshots" at nexus + "content/repositories/snapshots"
      } else {
        "releases" at nexus + "service/local/staging/deploy/maven2"
      }
    )
  },
  scalacOptions := DefaultOptions.scalac ++ Seq(Opts.compile.deprecation, "-feature", Opts.compile.unchecked, "-target:jvm-1.8"),
  scalaVersion := "2.13.4",
  scmInfo := Some(ScmInfo(
    browseUrl = url("https://github.com/christian-schlichtherle/bali-di"),
    connection = "scm:git:git@github.com/christian-schlichtherle/bali-di.git",
    devConnection = Some("scm:git:git@github.com/christian-schlichtherle/bali-di.git")
  )),
//  testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oF"),
))

lazy val annotations: Project = project
  .settings(
    name := "Bali DI Annotations",
    normalizedName := "bali-annotations",
  )

lazy val java: Project = project
  .dependsOn(annotations)
  .settings(
    javacOptions ++= Seq("-processor", "lombok.launch.AnnotationProcessorHider$AnnotationProcessor,lombok.launch.AnnotationProcessorHider$ClaimingProcessor"),
    libraryDependencies += "org.projectlombok" % "lombok" % "1.18.16" % Provided,
    libraryDependencies += ScalaTest % Test,
    name := "Bali DI for Java",
    normalizedName := "bali-java",
  )

lazy val parent: Project = project
  .in(file("."))
  .aggregate(annotations, java, samples)
  .settings(
    name := "Bali DI Parent",
    publishArtifact := false,
    releaseCrossBuild := false,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      releaseStepCommandAndRemaining("+test"),
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      releaseStepCommandAndRemaining("+publishSigned"),
      setNextVersion,
      commitNextVersion,
      pushChanges
    ),
  )

lazy val samples: Project = project
  .dependsOn(java)
  .settings(
    libraryDependencies += ScalaTest % Test,
    name := "Bali DI Samples",
    normalizedName := "bali-samples",
  )
