
[![License](https://img.shields.io/github/license/eitco/bom-maven-plugin.svg?style=for-the-badge)](https://opensource.org/license/mit)
[![Build status](https://img.shields.io/github/actions/workflow/status/eitco/manage-plugin-dependencies-maven-extension/deploy.yaml?branch=main&style=for-the-badge&logo=github)](https://github.com/eitco/manage-plugin-dependencies-maven-extension/actions/workflows/deploy.yaml)
[![Maven Central Version](https://img.shields.io/maven-central/v/de.eitco.cicd/manage-plugin-dependencies-maven-extension?style=for-the-badge&logo=apachemaven)](https://central.sonatype.com/artifact/de.eitco.cicd/manage-plugin-dependencies-maven-extension)

# manage-plugin-dependencies-maven-extension

This maven extension makes dependency management affect dependencies in the plugin section. There has been an
[issue](https://issues.apache.org/jira/browse/MNG-2496) about this for a long time, that only recently 
was closed - not to be fixed. 

It turns out however, that implementing a maven extension doing exactly this is pretty straightforward.

# use case 

Assume a pom.xml with the following content:

````xml
<project>
 ...
 
 <properties>
  <postgres.version>42.7.3</postgres.version>
 </properties>
 
 <dependencies>
  <dependency> <!-- (2) -->
   <groupId>org.postgresql</groupId>
   <artifactId>postgresql</artifactId>
   <version>${postgres.version}</version>
  </dependency>
 </dependencies>
 
 <build>
  <plugins>
   <plugin>
    <groupId>org.liquibase</groupId>
    <artifactId>liquibase-maven-plugin</artifactId>
    <executions>
     ... <!-- create a database schemas tables, views etc. -->
    </executions>
    <dependencies>
     <dependency> <!-- (1) -->
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <version>${postgres.version}</version>
     </dependency>
    </dependencies>
   </plugin>
   <plugin>
    <groupId>org.jooq</groupId>
    <artifactId>jooq-codegen-maven</artifactId>
    <executions>
     ... <!-- create classes from the schema created -->
    </executions>
    <dependencies>
     <dependency> <!-- (1) -->
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <version>${postgres.version}</version>
     </dependency>
    </dependencies>
   </plugin>
  </plugins>
 </build>
</project>
````
Of course one wants to make sure that both plugins use the jdbc client in the same version (1). Which should be 
the same as the jdbc client the application itself uses (2). 

In this example this is achieved using a property, but what if one needs to share this version in a way where 
properties cannot be used. For example, what if the jdbc drivers version is managed in the dependency management 
of an imported pom:

````xml

<project>
 ...

 <dependencyManagement>
  <dependencies>
   <dependency> <!-- (1) -->
    <groupId>my.organisation</groupId>
    <artifactId>organisation-bom</artifactId>
    <version>1.0.0</version>
    <type>bom</type>
    <scope>import</scope>
   </dependency>
  </dependencies>
 </dependencyManagement>

 <dependencies>
  <dependency> <!-- (2) -->
   <groupId>org.postgresql</groupId>
   <artifactId>postgresql</artifactId>
  </dependency>
 </dependencies>
 
 <build>
  <plugins>
   <plugin>
    <groupId>org.liquibase</groupId>
    <artifactId>liquibase-maven-plugin</artifactId>
    <executions>
     ... <!-- create a database schemas tables, views etc. -->
    </executions>
    <dependencies>
     <dependency> <!-- (3) -->
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
     </dependency>
    </dependencies>
   </plugin>
   <plugin>
    <groupId>org.jooq</groupId>
    <artifactId>jooq-codegen-maven</artifactId>
    <executions>
     ... <!-- create classes from the schema created -->
    </executions>
    <dependencies>
     <dependency> <!-- (3) -->
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
     </dependency>
    </dependencies>
   </plugin>
  </plugins>
 </build>
</project>
````
Now, the version is managed in a bill of materials which is imported (1), so that the version of the applications 
dependency may be omitted (2), the versions of the plugins dependencies (3), however are now unspecified. This would 
make maven fail the build while parsing the project. One could - of course - specify a property again, but it would 
not be guarantied that it is the same that is used in the bill of materials pom. 

This extension fixes that. It does inject itself in mavens process of reading a project and adds the correct managed dependency 
versions to dependencies declared in the plugin section that have no versions specified.

# usage

To activate the extension simply add a file `.mvn/extensions.xml` to the root of your project with the following content:

````xml
<extensions xmlns="http://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.0.0 http://maven.apache.org/xsd/core-extensions-1.0.0.xsd">

    <extension>
        <groupId>de.eitco.cicd</groupId>
        <artifactId>manage-plugin-dependencies-maven-extension</artifactId>
        <version>0.0.2</version>
    </extension>

</extensions>
````

> 📘 There are other ways to [activate core extensions](https://maven.apache.org/guides/mini/guide-using-extensions.html#core-extension).

Adding this file will enable maven to read (and execute) the example above - using the postgres clients version that 
is managed in the imported pom. 

> 📘 Some example projects can be found in the [integration tests](src/it)
