---
layout: page-fullwidth
title: "Downloads"
---

{% assign stable = (site.data.releases | where:"status", "stable" | first) %}

* [WebAnno {{ stable.version }} standalone (executable JAR)](https://bintray.com/artifact/download/webanno/downloads/webanno-standalone-{{ stable.version }}.jar) - [Installation instructions]({{ site.url }}/releases/{{ stable.version }}/docs/user-guide.html#sect_installation) 
* [WebAnno {{ stable.version }} WAR-archive](https://bintray.com/artifact/download/webanno/downloads/webanno-webapp-{{ stable.version }}.war) - [Installation instructions]({{ site.url }}/releases/{{ stable.version }}/docs/admin-guide.html#sect_installation)
* [SampleProjects Example projects]

You can subscribe to notifications for new releases via BinTray:  <a href="https://bintray.com/webanno/downloads/webanno2/_latestVersion"><img src="https://api.bintray.com/packages/webanno/downloads/webanno2/images/download.png"/></a>

## Upgrade from a previous version of WebAnno

### Exporting/importing

   * Log into WebAnno and export all the projects that you wish to migrate using the *Export* pane in the project settings
   * Move your WebAnno home folder to a safe location so that WebAnno and create a new home folder in the old location
   * Copy the *settings.properties* and *formats.properties* (if present) back from your moved folder
   * Start the new WebAnno version to initialize the database
   * Recreate the users
      * If you are using MySQL
         * create a new database for the new WebAnno version and update the *settings.properties* accordingly
         * use [mysqldump](http://dev.mysql.com/doc/refman/5.0/en/mysqldump.html) to dump the table `users`from the old database and load it back into the new database
      * If you are not using MySQL, you have to recreate the users manually
   * When upgrading to WebAnno 2.x from a pre 2.x version, remove the *format.properties* file from the WebAnno home folder
   * Restart WebAnno and import the previously exported projects

### In-place update

This method should work when updating only a bugfix version, e.g. from 2.0.9 to 2.0.10. When performing a minor or major update, better use the exporting/importing method above.  

   * *Make a backup of your data before upgrading to a new version:*
     * Make a copy of your WebAnno home folder
     * If you are using MySQL, make a backup of your WebAnno database, e.g. using the [http://dev.mysql.com/doc/refman/5.0/en/mysqldump.html mysqldump] command.
   * Standalone version
     * clean up the temporary installation before running the new version:
        * On OS X: `rm -R "$TMPDIR/winstoneEmbeddedWAR"`
        * On Linux: `rm -R /tmp/winstoneEmbeddedWAR`
        * On Windows: remove the `winstoneEmbeddedWAR` that should be somewhere under `C:\Users\<username>\AppData\Local\Temp`
     * start the new version
   * WAR version
     * while Tomcat is running, delete the old WAR from your `webapps`folder
     * wait until Tomcat has automatically deleted the WebAnno folder 
     * stop Tomcat
     * place the new WAR file into your `webapps` folder
     * start Tomcat
