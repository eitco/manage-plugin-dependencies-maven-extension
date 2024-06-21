
[![License](https://img.shields.io/github/license/eitco/bom-maven-plugin.svg?style=for-the-badge)](https://opensource.org/license/mit)


<!-- TODO: this is the documentation for the eitco github maven project template - it will however be generated with 
a project if used. In your project simply replace the content of this file with the documentation of your project.
consider the following additional badges (and adapt them for your project):

[![Build status](https://img.shields.io/github/actions/workflow/status/eitco/<your github project name>/deploy.yaml?branch=main&style=for-the-badge&logo=github)](https://github.com/eitco/<your github project name>/actions/workflows/deploy.yaml)
[![Maven Central Version](https://img.shields.io/maven-central/v/<groupId>/<artifactId>?style=for-the-badge&logo=apachemaven)](https://central.sonatype.com/artifact/<groupId>/<artifactId>)

also check whether the license badge link points to the correct license.
-->

# eitco maven project template

This [github template repository](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-repository-from-a-template) 
contains a basic maven project ready for the eitco open source ci. It contains:

# README.md

This readme, you are currently viewing. In a generated project it needs to be replaced. See the TODO-commentary for 
some ideas.

# LICENSE

By default, eitco open source software is licensed under the [MIT license](https://opensource.org/license/mit). 
To change this, simply replace this file

# continuous integration

The directories `.github` and `deployment` contain the CI. While the directory `.github` contains actions that build 
each commit and release the project on demand, the directory `deployment` contains configuration for the release.
A lot of the build however is configured by the project object model (pom.xml).

# pom.xml

This file specifies the build for your project. Make sure to adapt it according to the TODOs. A lot of the 
CI is inherited from the [`eitco-oss-parent`](https://github.com/eitco/eitco-oss-parent) pom.

# .mvn

The `.mvn` directory activates and configures the `maven-git-versioning-extension`. This extension changes the 
projects version depending on the current branch. This way every branch can be deployed without their artifacts
overriding each other.
