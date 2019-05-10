
# Gerrit development instructions at SISCOG

## Objectives

The objectives of this document are to provide specific instructions for the development and building of [Gerrit](https://www.gerritcodereview.com/) at SISCOG. It does not contain all the generic instructions related to [Gerrit](https://www.gerritcodereview.com/) development, those are accessible in its [Documentation](https://gerrit-documentation.storage.googleapis.com/Documentation/2.16.8/index.html), in the chapter _Developer_ (note that the links here to the Gerrit documentation might become obsolete, but, they are provided here nevertheless for convenience).

**TODO** This document is missing the setup details of gerrit-vdev VM by Support, what are the installed versions of support software (bazel, Git, Eclipse, etc).

**OPTIONAL** Note that the name of this file reveals the ambition that it might also capture the administrative details of Gerrit at SISCOG, such as production environment and procedures for handling administrative tasks. Alas, for now it doesn't contain any of this.

## Specific Gerrit developments by SISCOG

The specific developments by SISCOG of Gerrit are documented in several cards of the [CRI -> Git Trello board](https://trello.com/b/sJRmHQfF/cri-%E2%87%A8-git). Specifically, important cards are:

* ~~Preparar setup para compilar Gerrit na SISCOG~~
* Gerrit
* highlight.js pull request
* Contribute Gerrit CommitModifier plugin upstream

## Gerrit development environment at SISCOG

Gerrit development at SISCOG is made in a Linux (CentOS) VM lxgerritvdev-v1,
aliased to **_gerrit-vdev_**. You access it with _Git Bash_ via SSH, using your SISCOG credentials (i.e., user's LDAP password):

```$ ssh gerrit-vdev```

### Setup GUI applications

The GUI applications like the [Eclipse JDT](https://www.eclipse.org/jdt/) use XServer and are open in the computer the user connects from via SSH, using the local [Xming X Server for Windows](https://sourceforge.net/projects/xming/) which is installed by SISCOG's Support in `C:\siscog-dev-tools\utilities\Xming`.

To enable this, edit the file `~/.bashrc`, adding:

```
xserver=($SSH_CLIENT)
export DISPLAY=$xserver:1
```

After this, reload `~/.bashrc` with:

```$ . ~/.bashrc```

Now you must launch in the local computer the Xming X server by starting it with the shortcut `C:\siscog-dev-tools\utilities\Xming\Xming`.

### Setup Gerrit clone

The folder where the clone of Gerrit is placed is `/gerrit/gerrit-vdev/gerrit`.

If needed this can easily be re-created with:

```
/gerrit/gerrit-vdev$ rm -rf gerrit
/gerrit/gerrit-vdev$ git clone --recurse-submodules https://gerrit.googlesource.com/gerrit
/gerrit/gerrit-vdev$ cd gerrit
/gerrit/gerrit-vdev/gerrit$ git remote add siscog git@gitlab.siscog:open-source/gerrit.git
/gerrit/gerrit-vdev/gerrit$ git fetch siscog
/gerrit/gerrit-vdev/gerrit$ git checkout magic-dates
/gerrit/gerrit-vdev/gerrit$ git submodule update --init --recursive
```

Resources:
1. [Gerrit Developer; Getting the source](https://gerrit-documentation.storage.googleapis.com/Documentation/2.16.8/dev-readme.html#_getting_the_source)
2. [SISCOG Gerrit repository in SISCOG's GitLab](https://gitlab.siscog/open-source/gerrit)

### Merging latest Gerrit's `stable-M.N` branch into SISCOG's `magic-dates` branch

The `magic-dates` branch should be checked out in the Git repository you are making the merge. The Gerrit's stable branch named according `stable-M.N`, where M and N are major and minor version numbers is the topic branch to merge into `magic-dates`.

```
/gerrit/gerrit-vdev/gerrit$ git merge --no-commit origin/stable-M.N
```

Expected conflicts:

* The file `lib/highlightjs/highlight.min.js` contains improvements by SISCOG in the Common Lisp language highlighting and therefore prefer the version in `magic-dates`. (When these improvements are contributed and merged to the upstream project this won't be needed any longer. See corresponding Trello card.)

Then you must be careful to review the `plugins` submodules and prefer the commits in the `stable-M.N` branch. An example on how to checkout specific submodules' commits, looking for the appropriate SHAs from the diff and performing the checkouts and adding them to the staging are:

```
/gerrit/gerrit-vdev/gerrit$ cd plugins/codemirror-editor/
/gerrit/gerrit-vdev/gerrit/plugins/codemirror-editor$ git checkout <sha1-of-stable-M.N-commit>
/gerrit/gerrit-vdev/gerrit/plugins/codemirror-editor$ cd ../download-commands
/gerrit/gerrit-vdev/gerrit/plugins/download-commands$ git checkout <sha1-of-stable-M.N-commit>
/gerrit/gerrit-vdev/gerrit/plugins/download-commands$ cd ../hooks
/gerrit/gerrit-vdev/gerrit/plugins/hooks$ git checkout <sha1-of-stable-M.N-commit>
/gerrit/gerrit-vdev/gerrit/plugins/hooks$ cd ../replication
/gerrit/gerrit-vdev/gerrit/plugins/replication$ git checkout <sha1-of-stable-M.N-commit>
/gerrit/gerrit-vdev/gerrit/plugins/replication$ cd ../singleusergroup
/gerrit/gerrit-vdev/gerrit/plugins/singleusergroup$ git checkout <sha1-of-stable-M.N-commit>
/gerrit/gerrit-vdev/gerrit/plugins/singleusergroup$ cd ../../
/gerrit/gerrit-vdev/gerrit$ git add --update
```

Finally finish the merge, tag and push to the siscog (GitLab) remote. The SISCOG specific tag `vM.N.S-magic-dates` (and eventual tags `vM.N.S.S2-magic-dates` if needed) follow the Gerrit tag `vM.N.S` of the latest Gerrit version out of the `stable-M.N branch`.

```
/gerrit/gerrit-vdev/gerrit$ git merge --continue
/gerrit/gerrit-vdev/gerrit$ git tag vM.N.S-magic-dates
/gerrit/gerrit-vdev/gerrit$ git push siscog vM.N.S-magic-dates
```

### Setup your user's home dir to `/tmp/$LOGNAME` or you'll fail!

Due to [Bazel](https://bazel.build/), its Gerrit specific build and Eclipse placing lots of content in the user's `HOME` folder, exceeding the disk quota and causing hard to understand failures, you need to temporarily set your `HOME` to `/tmp/$LOGNAME` and place there some specific files:

```
$ export HOME=/tmp/$LOGNAME
$ cp /home/$LOGNAME/.bashrc ~/.bashrc
$ cp /home/$LOGNAME/.bash_profile /tmp/$LOGNAME/.bash_profile
```

Since this is going to be needed often, automate it in the script `set_user_home_in_tmp.sh`:

```
$ cat > set_user_home_in_tmp.sh << 'EOF'
> #!/usr/bin/bash
> export HOME=/tmp/\$LOGNAME
> cp --update /home/\$LOGNAME/.bashrc /tmp/\$LOGNAME/.bashrc
> cp --update /home/\$LOGNAME/.bash_profile /tmp/\$LOGNAME/.bash_profile
> EOF
$ chmod u+x set_user_home_in_tmp.sh
```

Then, whenever you want this, _source_ it from your current shell:

```
~$ . set_user_home_in_tmp.sh
/home/<logname>/$
```

### Building Gerrit

The reference for this is [Building with Bazel](https://gerrit-documentation.storage.googleapis.com/Documentation/2.16.8/dev-bazel.html).

You build it with the command:

```
/gerrit/gerrit-vdev/gerrit$ bazel build release
```

To execute the automated unit tests:

```
/gerrit/gerrit-vdev/gerrit$ bazel test --build_tests_only //...
```

### Development and debugging with Eclipse

#### Setup Eclipse development

Reference documentation for this in [Eclipse](https://gerrit-documentation.storage.googleapis.com/Documentation/2.16.8/dev-bazel.html#_eclipse).

If it wasn't done yet, you must generate the Eclipse project:

```
/gerrit/gerrit-vdev/gerrit$ ./tools/eclipse/project.py
```

To launch Eclipse JDT (you must start Xming before in your computer):

```$ /gerrit/eclipse/eclipse -user $HOME &```

Due to the disk quota problems in the default `/home/$LOGNAME` directory, using Eclipse JDT was only successful after setting `$HOME` to `/tmp/$LOGNAME` directory (see above) and because Eclipse doesn't use the `HOME` environment variable, you have to specifically indicate the `user` _location_ to it in the command line.

Remaining details of using Eclipse for Gerrit development are given in the above reference documentation, such as how to create a local Debug launch configuration, etc.

### Test Gerrit environment

After building the Gerrit release.war it must be tested in the **_gerrit-vdev_** environment before graduating to the production environment.

**TODO** Update this description after Marco finishes the setup.

The development test environment is located in `\gerrit\gerrit-vdev\gerrit-install`. It was prepared by user _luis_ and will only work for him.

The instructions followed to prepare this are documented in [Initialize the Site](https://gerrit-documentation.storage.googleapis.com/Documentation/2.16.8/install.html#init).

The site initialization was performed via:

```
/gerrit/gerrit-vdev/gerrit$ cp bazel-bin/release.war ../gerrit-install/release.war
/gerrit/gerrit-vdev/gerrit$ pushd ../gerrit-install
/gerrit/gerrit-vdev/gerrit-install$ export site_path=/gerrit/gerrit-vdev/gerrit-install
/gerrit/gerrit-vdev/gerrit-install$ java -jar release.war init -d /gerrit/gerrit-vdev/gerrit-install
```

Then you follow the interactive command line configuration procedure which results in the Gerrit installation being initialized according to it in the `/gerrit/gerrit-vdev/gerrit-install` directory.

To start, stop and restart Gerrit server use the script `bin/gerrit.sh` according to:

```
/gerrit/gerrit-vdev/gerrit-install$ bin/gerrit.sh start
/gerrit/gerrit-vdev/gerrit-install$ bin/gerrit.sh stop
/gerrit/gerrit-vdev/gerrit-install$ bin/gerrit.sh restart
```

If a new version of release.war is built, you'll need to re-initialize the installation with it again. It is the same when there is the need to perform an upgrade in production. This is the same as for the initial initialization which is described above, but, this time you'll probably accept all the default values given by the interactive command line configuration procedure since it is picking these values from the existing configuration.
