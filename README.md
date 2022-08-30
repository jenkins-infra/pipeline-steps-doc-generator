# pipeline-steps-doc-generator

This project generates the documentation for pipeline jobs.

## Development

In order to install and run the project on your local machine, a rough outline of the steps is provided below.

### 1. Clone Repositories

To setup a development environment for this repository, you will need to clone the following repositories.

* [`jenkins-infra/pipeline-steps-doc-generator`](https://github.com/jenkins-infra/pipeline-steps-doc-generator/)
* [`jenkins-infra/jenkins.io`](https://github.com/jenkins-infra/jenkins.io)
* [`jenkinsci/schedule-build-plugin`](https://github.com/jenkinsci/schedule-build-plugin)

>**NOTE:** The plugin `jenkinsci/schedule-build-plugin` is just used as an example. You can follow the similar instructions to generate the pipeline steps documentation for other plugins as well.

### 2. Arrange Files

Make sure that the file structure on your local machine matches the one shown below.

```
├── ...
│   ├── jenkins-infra
│   │   ├── pipeline-steps-doc-generator
|   |   ├── jenkins.io
│   ├── jenkinsci
│   │   ├── schedule-build-plugin
```

### 3. Patch `jenkins.io`

You will need to temporarily patch `jenkins.io` as shown below. This is done so that it does not fetch the existing AsciiDoc from external resources,
but uses the AsciiDoc for the `schedule-build-plugin` generated locally.

* Navigate to `jenkins.io/scripts/fetch-external-resources`.
* Comment out the following lines from the code.

```diff
-  [
-    'https://ci.jenkins.io/job/Infra/job/pipeline-steps-doc-generator/job/master/lastSuccessfulBuild/artifact/allAscii.zip',
-    'content/_tmp/allAscii.zip',
-    nil,
-    'content/doc/pipeline/steps'
-  ],

+ # [
+ #   'https://ci.jenkins.io/job/Infra/job/pipeline-steps-doc-generator/job/master/lastSuccessfulBuild/artifact/allAscii.zip',
+ #   'content/_tmp/allAscii.zip',
+ #   nil,
+ #   'content/doc/pipeline/steps'
+ # ],
```

### 4. Create `Makefile`

* Create a file named `Makefile` inside the `jenkinsci/schedule-build-plugin` folder.
* Insert the following code in the file.

```Makefile
TAG=$(shell date -I -u)

copy-plugins:
     if [ \! -f target/test-classes/test-dependencies/index -o \
          pom.xml -nt target/test-classes/test-dependencies/index ]; then \
          mvn clean validate hpi:resolve-test-dependencies; fi
     rm -rf plugins
     mkdir plugins
     cp -v target/test-classes/test-dependencies/*.hpi plugins
 ```

> The `copy-plugins` command copies the plugins into the target folder.

### 5. Run Commands

Run the following commands with current directory set to `jenkins-infra/pipeline-steps-doc-generator`.

> **NOTE:** The commands below currently work only with Linux and MacOS. If you are using Windows, the easiest way would be to use [WSL](https://docs.microsoft.com/en-us/windows/wsl/).

* Remove previously existing pipeline steps AsciiDoc from `jenkins.io`, if any.

```Shell
rm -v ../jenkins.io/content/doc/pipeline/steps/*.adoc
```

* Run the `Makefile` created in [Step 4](#4-create-makefile).

>**OPTIONAL:** Add `plugins` folder to the `.gitignore` file of all the plugins on which you wish to run the code to prevent flooding the git tracker of your IDE/Editor.

```Shell
make -C ../../jenkinsci/schedule-build-plugin copy-plugins
```

* Install the `pipeline-steps-doc-generator` modules.

```Shell
mvn clean install
```

* Run this project and generate the documentation.

> **NOTE:** If you are working with another plugin, replace the path after `-homeDir $(pwd)/../../` with that of your plugin.

```Shell
mvn "-Dexec.args=-classpath %classpath org.jenkinsci.pipeline_steps_doc_generator.PipelineStepExtractor -homeDir $(pwd)/../../jenkinsci/schedule-build-plugin -asciiDest $(pwd)/../jenkins.io/content/doc/pipeline/steps -declarativeDest /tmp/declarative" -Dexec.executable=$(which java) org.codehaus.mojo:exec-maven-plugin:3.0.0:exec
```

* Finally, build and run the `jenkins.io` website.

```Shell
make -C ../jenkins.io run
```

You can then browse to [Pipeline Steps Reference](http://localhost:4242/doc/pipeline/steps/) to see the running instance of `jenkins.io` on your `localhost`.

### 6. Configure Parameters

The parameters that need to be separated to new pages can be entered in `config.txt` by adhering to the following rules. This feature reduces the content on longer pages, thus increasing the loading speed of these pages.

* Ensure that a specified parameter's documentation is the same everywhere it occurs in the Pipeline Steps Reference. For example, `perforce` contains different documentation under the checkout step's scm parameter and the scanForIssues step's tool parameter. Hence, it can not be included in the configuration file.

* Maintain the order of the parameters such that if one parameter occurs inside the nesting of another, it is written above the other in the configuration file. For example, `$class: 'GitSCM'` is present inside `$class: MultiSCM` in checkout step, hence, it must be written above in the configuration file.

* A parameter must have atleast 100 lines of asciidoc code present in the location from which it is supposed to be removed.
