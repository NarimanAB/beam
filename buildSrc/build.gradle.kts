/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * License); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an AS IS BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Plugins for configuring _this build_ of the module
plugins {
  `java-gradle-plugin`
  groovy
  id("com.diffplug.spotless") version "5.6.1"
}

// Define the set of repositories required to fetch and enable plugins.
repositories {
  jcenter()
  maven { url = uri("https://plugins.gradle.org/m2/") }
  maven {
    url = uri("https://repo.spring.io/plugins-release/")
    content { includeGroup("io.spring.gradle") }
  }
}

// Dependencies on other plugins used when this plugin is invoked
dependencies {
  compile(gradleApi())
  compile(localGroovy())
  compile("com.github.jengelman.gradle.plugins:shadow:6.1.0")
  compile("gradle.plugin.com.github.spotbugs.snom:spotbugs-gradle-plugin:4.5.0")

  runtime("net.ltgt.gradle:gradle-apt-plugin:0.21")                                                    // Enable a Java annotation processor
  runtime("com.google.protobuf:protobuf-gradle-plugin:0.8.13")                                          // Enable proto code generation
  runtime("io.spring.gradle:propdeps-plugin:0.0.9.RELEASE")                                            // Enable provided and optional configurations
  runtime("com.commercehub.gradle.plugin:gradle-avro-plugin:0.11.0")                                   // Enable Avro code generation
  runtime("com.diffplug.spotless:spotless-plugin-gradle:5.6.1")                                       // Enable a code formatting plugin
  runtime("gradle.plugin.com.github.blindpirate:gogradle:0.11.4")                                      // Enable Go code compilation
  runtime("gradle.plugin.com.palantir.gradle.docker:gradle-docker:0.22.0")                             // Enable building Docker containers
  runtime("gradle.plugin.com.dorongold.plugins:task-tree:1.5")                                       // Adds a 'taskTree' task to print task dependency tree
  runtime("com.github.jengelman.gradle.plugins:shadow:6.1.0")                                          // Enable shading Java dependencies
  runtime("ca.coglinc:javacc-gradle-plugin:2.4.0")                                                     // Enable the JavaCC parser generator
  runtime("net.linguica.gradle:maven-settings-plugin:0.5")
  runtime("gradle.plugin.io.pry.gradle.offline_dependencies:gradle-offline-dependencies-plugin:0.5.0") // Enable creating an offline repository
  runtime("net.ltgt.gradle:gradle-errorprone-plugin:1.2.1")                                           // Enable errorprone Java static analysis
  runtime("org.ajoberstar.grgit:grgit-gradle:4.0.2")                                                   // Enable website git publish to asf-site branch
  runtime("com.avast.gradle:gradle-docker-compose-plugin:0.13.2")                                       // Enable docker compose tasks
  runtime("ca.cutterslade.gradle:gradle-dependency-analyze:1.4.3")                                     // Enable dep analysis
  runtime("gradle.plugin.net.ossindex:ossindex-gradle-plugin:0.4.11")                                  // Enable dep vulnerability analysis
  runtime("org.checkerframework:checkerframework-gradle-plugin:0.5.16")                                 // Enable enhanced static checking plugin
  runtime("org.apache.solr:solr-solrj:8.5.2")
}

// Because buildSrc is built and tested automatically _before_ gradle
// does anything else, it is not possible to spotlessApply because
// spotlessCheck fails before that. So this hack allows disabling
// the check for the moment of application.
//
// ./gradlew :buildSrc:spotlessApply -PdisableSpotlessCheck=true
val disableSpotlessCheck: String by project
val isSpotlessCheckDisabled = true
//project.hasProperty("disableSpotlessCheck") && disableSpotlessCheck == "true"

spotless {
  isEnforceCheck = !isSpotlessCheckDisabled
  groovy {
    excludeJava()
    greclipse().configFile("greclipse.properties")
  }
  groovyGradle {
    greclipse().configFile("greclipse.properties")
  }
}

gradlePlugin {
  plugins {
    create("beamModule") {
      id = "org.apache.beam.module"
      implementationClass = "org.apache.beam.gradle.BeamModulePlugin"
    }
    create("vendorJava") {
      id = "org.apache.beam.vendor-java"
      implementationClass = "org.apache.beam.gradle.VendorJavaPlugin"
    }
    create("beamJenkins") {
      id = "org.apache.beam.jenkins"
      implementationClass = "org.apache.beam.gradle.BeamJenkinsPlugin"
    }
  }
}
