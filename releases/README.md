# releases/

Output of `scripts/package.sh` (`mvn package -Pdist`): the self-contained distributions
and a copy of the jar, ready to attach to a GitHub Release.

```
xsdviewer-<version>-windows.zip    XsdViewer.exe + xsdviewer.bat, bundled JRE
xsdviewer-<version>-linux.tar.gz   xsdviewer.sh, bundled JRE
xsdviewer-<version>.jar            needs Java 21: java -jar xsdviewer-<version>.jar
```

Everything here except this file is git-ignored; a build first deletes the artefacts
of the previous one, whatever their version.

The official releases can be found here: https://github.com/j4ckk0/XsdViewer/releases
