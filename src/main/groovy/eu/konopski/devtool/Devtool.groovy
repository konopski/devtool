package eu.konopski.devtool

import org.fusesource.jansi.AnsiConsole

import java.util.zip.ZipFile

import static org.fusesource.jansi.Ansi.ansi

@SuppressWarnings("GrMethodMayBeStatic")
class Devtool {
    private static def sepChar = File.separatorChar
    private static def pathSepChar = File.pathSeparatorChar
    private def devtoolName = "devtool"
    def devToolVersion = "1.40"
    boolean debugOutput = false

    static def CONTACT_PERSON
    static def CONFLUENCE_SPACENAME
    static def CONFLUENCE_URL
    static def NEXUS_SERVER_URL
    static def NEXUS_REPOSITORY

    static void main(String[] args) {
        loadProperties()
        AnsiConsole.systemInstall()

        Devtool devtool = new Devtool()
        devtool.checkForUpdatesToDevTool()
        devtool.parseInput(args)

        AnsiConsole.systemUninstall()
    }

    static def loadProperties() {
        def properties = new Properties()
        this.getClass().getResource('/devtool.conf').withInputStream {
            properties.load(it)
        }
        CONTACT_PERSON = properties.getProperty("CONTACT_PERSON")
        CONFLUENCE_SPACENAME = properties.getProperty("CONFLUENCE_SPACENAME")
        CONFLUENCE_URL = properties.getProperty("CONFLUENCE_URL")
        NEXUS_SERVER_URL = properties.getProperty("NEXUS_SERVER_URL")
        NEXUS_REPOSITORY = properties.getProperty("NEXUS_REPOSITORY")
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    void parseInput(String[] args) {
        def cli = new CliBuilder(usage: 'devtool [command] [options]', header: 'Options:', width: 98, footer: '\n' +
                'Docs and FAQ:\n' + ansi().fgBrightBlue().a("https://github.com/NordeaOSS/devtool/blob/master/usage.md\n").reset() +
                '\nChangelog:\n' + ansi().fgBrightBlue().a('"See the changelog.md in your local devtool installation"\n').reset() +
                '\nAdd a watch on the tools/versions blog:\n' + ansi().fgBrightBlue().a("$CONFLUENCE_URL/pages/viewrecentblogposts.action?key=" + CONFLUENCE_SPACENAME).reset())

        cli.with {
            h longOpt: 'help', 'print this message'
            info args: 1, valueSeparator: ' ', argName: 'toolname', 'Opens up the description page for the tool'
            install args: 2, valueSeparator: ' ', argName: 'toolname [version]', 'install the given tool with the specific version. For example: "jdk 1.5.0". Or just "jdk" for the latest version'
            uninstall args: 2, valueSeparator: ' ', argName: 'toolname [version]', 'Uninstalls the given tool with the specific version. For example: "maven 3.2.5"'
            listupdates args: 0, 'Shows the possible updates'
            updateall args: 0, 'Updates all tools to the latest versions'
            list args: 0, 'Lists all the possible tools'
            setup args: 2, valueSeparator: ' ', argName: 'toolname version', 'Sets up the given tools path with the specific version. For example: "jdk 1.5.0"'
            debug args: 0, 'Adds debug info'
            upload args: 1, valueSeparator: ' ', argName: 'path to the zipped tool', 'Uploads a new tool to nexus'
            register args: 1, valueSeparator: ' ', argName: 'path to registry XML', 'Registers a tool from existing Nexus location via XML manifest'
            listnotinstalled args: 0, 'Shows the list of available tools not installed in any version'
        }

        def options = cli.parse(args)
        if (!options) {
            return
        }

        if (options.debug) {
            debugOutput = true
        }

        if (options.info) {
            openToolInfoPage(options.info)
        } else if (options.install) {
            installTool(options.install, options.arguments().size() > 0 ? options.arguments().get(0) : null)
            println "\nContact $CONTACT_PERSON for getting new tools into the repository"
            println ansi().fgBrightRed().a("\n\nNB NB NB ").reset()
            println "You have to start a new commandprompt to use tools that have modified the path!\nFor example after installing a new jdk and maven"
        } else if (options.setup) {
            setupTool(options.setup, options.arguments().size() > 0 ? options.arguments().get(0) : null)
            println "\n\n NB NB NB You have to start a new commandprompt to use the modified tool version!"
        } else if (options.listupdates) {
            println "Updates available:"
            println listAndInstallUpdates(false)

        } else if (options.updateall) {
            def output = listAndInstallUpdates(true)
            println output

        } else if (options.list) {
            def output = listTools()
            println output

        } else if (options.uninstall) {
            if (options.arguments().isEmpty()) {
                println "You need to specify both toolname and version"
                System.exit(0)
            }
            uninstallTool(options.uninstall, options.arguments().size() > 0 ? options.arguments().get(0) : null)

        } else if (options.listnotinstalled) {
            println "Tools available:"
            println listNotInstalled()

        } else if (options.upload) {
            uploadTool(options.upload)
        } else if (options.register) {
            registerTool(options.register)
        } else {
            printUsage(cli)
        }

        logArgs(args)
    }

    def openToolInfoPage(String toolName) {
        debugln "show info for toolName = $toolName"
        "cmd /c \"start $CONFLUENCE_URL/display/$CONFLUENCE_SPACENAME/tools+$toolName\"".execute()
    }

    def createBlogPost(String toolName, String toolVersion, String confluenceUsername, String confluencePassword) {
        println "Creating blogpost..."

        def uploadCommand = """curl -g -k -u $confluenceUsername:$confluencePassword -H "Content-Type:application/json" -X POST -d "{\\"type\\":\\"blogpost\\",\\"title\\":\\"new tool/version added to devtool: $toolName-$toolVersion\\",\\"space\\":{\\"key\\":\\"$CONFLUENCE_SPACENAME\\"},\\"body\\":{\\"storage\\":{\\"value\\":\\"install new version with: devtool -install $toolName\\",\\"representation\\":\\"storage\\"}}}" $CONFLUENCE_URL/rest/api/content/"""
        def process = runCurlCommand(uploadCommand)
        if (process.exitValue() != 0) {
            println "Problem with creating blogpost"
        }
    }

    private Process runCurlCommand(String curlString) {
        debugln "curlCommand = $curlString"
        Process p = curlString.execute()

        def outputStream = new StringBuffer()
        def errorStream = new StringBuffer()
        p.waitForProcessOutput(outputStream, errorStream)

        debugln "$outputStream"
        debugln "errorStream = $errorStream"
        debugln "errorId = ${p.exitValue()}"
        return p
    }

    def uploadTool(String toolpath) {
        Console console = System.console()
        String nexusUsername = console.readLine("Nexus username: ")
        char[] nexusPassword = console.readPassword("Enter password for nexus: ")

        String confluenceUsername = console.readLine("Confluence username: ")
        char[] confluencePassword = console.readPassword("Enter password for confluence: ")

        println "Uploading tool $toolpath"

        def toolNameAndVersion = toolpath.substring(toolpath.lastIndexOf('\\') + 1)
        if (!verifyToolName(toolNameAndVersion)) {
            return
        }

        if (!verifyZipFile(toolpath)) {
            return
        }

        def toolName = extractToolName(toolNameAndVersion)
        def toolVersion = extractToolVersion(toolNameAndVersion)

        def uploadCommand = "curl --insecure -v -F r=$NEXUS_REPOSITORY -F hasPom=false -F e=zip -F g=devtool -F a=$toolName -F v=$toolVersion -F p=zip -F file=@$toolpath -u $nexusUsername:$nexusPassword https://ninja-nexus.oneadr.net/nexus/service/local/artifact/maven/content"
        debugln "uploadCommand = $uploadCommand"
        Process p = uploadCommand.execute()

        def outputStream = new StringBuffer()
        def errorStream = new StringBuffer()
        p.waitForProcessOutput(outputStream, errorStream)

        println "$outputStream"
        debugln "errorStream = $errorStream"
        debugln "errorId = ${p.exitValue()}"

        if (p.exitValue() == 26) {
            println "Could not open file: $toolpath"
            return
        } else if (errorStream.contains("401 Unauthorized")) {
            println "Incorrect nexusPassword!"
            return
        } else if (p.exitValue() != 0) {
            println "An unknown error occured while uploading tool."
            return
        }

        println "creating and uploading new ToolsAndVersionsFile..."
        def toolsAndVersionsFile = buildToolsAndVersionsFile()
        deleteOldToolsAndVersionsFile(nexusUsername, nexusPassword as String)
        uploadToolsAndVersionsFile(nexusUsername, nexusPassword as String, toolsAndVersionsFile)

        createBlogPost(toolName, toolVersion, confluenceUsername, confluencePassword.toString())
    }

    def registerTool(String registryXmlPath) {
        Console console = System.console()
        String nexusUsername = console.readLine("Nexus username: ")
        char[] nexusPassword = console.readPassword("Enter password for nexus: ")

        String confluenceUsername = console.readLine("Confluence username: ")
        char[] confluencePassword = console.readPassword("Enter password for confluence: ")

        println "Registering tool from $registryXmlPath"

        // Verify file exists
        def registryFile = new File(registryXmlPath)
        if (!registryFile.exists()) {
            println "Could not find file $registryXmlPath"
            return
        }

        // Parse XML to get tool name and versions
        def registry = new XmlSlurper().parse(registryFile)
        def toolName = registry.name.text()

        if (!toolName) {
            println "Registry XML must contain <name> element with tool name"
            return
        }

        def versions = registry.versions.version
        if (versions.size() == 0) {
            println "Registry XML must contain at least one <version> element"
            return
        }

        def versionsList = []
        versions.each { versionsList.add(it.@number.text()) }

        println "Tool name: $toolName"
        println "Versions found: ${versionsList.join(', ')}"

        // Upload registry XML to Nexus
        def targetUrl = "$NEXUS_SERVER_URL/nexus/content/repositories/$NEXUS_REPOSITORY/devtool/registry/${toolName}.xml"

        println "Uploading registry to Nexus..."
        def passwordString = new String(nexusPassword)
        def uploadCommand = "curl --insecure -v -u $nexusUsername:$passwordString --upload-file \"$registryXmlPath\" \"$targetUrl\""
        debugln "uploadCommand = $uploadCommand"

        Process p = uploadCommand.execute()
        def outputStream = new StringBuffer()
        def errorStream = new StringBuffer()
        p.waitForProcessOutput(outputStream, errorStream)

        debugln "$outputStream"
        debugln "errorStream = $errorStream"
        debugln "exitValue = ${p.exitValue()}"

        if (errorStream.contains("401 Unauthorized")) {
            println "Incorrect Nexus credentials!"
            return
        } else if (p.exitValue() != 0) {
            println "Error uploading registry XML to Nexus"
            println "Error: $errorStream"
            return
        }

        println ansi().fgGreen().a("Registry uploaded successfully").reset()

        // Regenerate tools and versions index
        println "Regenerating tool index..."
        def toolsAndVersionsFile = buildToolsAndVersionsFile()
        deleteOldToolsAndVersionsFile(nexusUsername, passwordString)
        uploadToolsAndVersionsFile(nexusUsername, passwordString, toolsAndVersionsFile)

        println ansi().fgGreen().a("Tool index updated").reset()

        // Create blog post about the registered tool - use first version as representative
        def representativeVersion = versionsList.get(0)
        createBlogPost(toolName, representativeVersion, confluenceUsername, confluencePassword.toString())

        println ansi().fgGreen().a("\nTool '$toolName' registered successfully!").reset()
        println "\nUsers can now install it with: devtool -install $toolName"
    }

    boolean verifyZipFile(String toolpath) {
        if (!new File(toolpath).exists()) {
            println "Could not find file $toolpath"
            return false
        }

        // Verify it's a valid ZIP file
        try {
            def zipFile = new ZipFile(toolpath)
            zipFile.close()
        } catch (Exception e) {
            println "Invalid ZIP file: ${e.message}"
            return false
        }

        // Check if it follows strict format (version folder)
        if (zipped_tool_have_version_folder(toolpath) &&
            zipped_tool_version_does_not_match(toolpath) &&
            zipped_tool_have_version_folder_only(toolpath)) {
            debugln "ZIP follows strict devtool format"
            return true
        }

        // Relaxed mode - accept any valid ZIP structure
        println "ZIP doesn't follow strict format, but will be accepted and reorganized during installation"
        return true
    }

    String extractToolVersion(String toolNameAndVersion) {
        debugln "extractToolVersion $toolNameAndVersion"
        String[] result = ""
        toolNameAndVersion.eachMatch("-([\\d.]+)") {
            result = it
        }
        if (result.length == 0) return ""
        return result[1].substring(0, result[1].length() - 1)
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    String extractToolName(String toolNameAndVersion) { // c:\\sdds\\devtool-111.11.zip
        debugln "extractToolName $toolNameAndVersion"

        // Remove .zip extension if present
        def nameWithoutExt = toolNameAndVersion.replaceAll(/\.zip$/, '')

        // Try to match pattern: anything-version where version is digits and dots
        def matcher = nameWithoutExt =~ /^(.+?)-(\d+[\d.]*)$/
        if (matcher) {
            return matcher[0][1]  // Return everything before the last version
        }

        // Fallback to old behavior - just get first word
        matcher = nameWithoutExt =~ /(\w+)/
        if (matcher.getCount() > 0) {
            return matcher[0][0]
        }
        return ""
    }

    boolean verifyToolName(String toolName) {
        boolean verified = false
        // Removed check for multiple hyphens - now supports names like apache-maven-3.8.6.zip
        if (!extractToolName(toolName).isEmpty() && !extractToolVersion(toolName).isEmpty()) {
            verified = true
        }
        if (!verified) {
            println "Naming of file is not correct. Should be eg: 'toolname-X.X.X.zip' or 'my-tool-name-X.X.X.zip'"
        }
        return verified
    }

    protected void logArgs(String[] argsString) {
        def userName = System.getenv("USERNAME")
        def userDir = System.getenv("USERPROFILE")
        def logfileName = userDir + "/.devtool"
        logArgs(logfileName, argsString.toString(), userName)
    }

    protected void logArgs(String logfileString, String argsString, String userName) {
        try {
            File logfile = new File(logfileString)
            if (!logfile.exists()) {
                logfile.createNewFile()
            }

            def timestamp = new Date()
            logfile.append("$timestamp;$userName;$argsString\n")
        } catch (Exception ignore) {
            ignore.printStackTrace()
        }
    }

    private void printUsage(CliBuilder cli) {
        println "Devtool version: $devToolVersion"
        cli.usage()
    }

    private void checkForUpdatesToDevTool() {
        try {
            def localVersion = devToolVersion
            def remoteVersion = getLatestVersionForToolFromRemote(devtoolName)

            print("Checking if devtool is up to date...")

            if (isRemoteVersionNewest(remoteVersion, localVersion)) {
                printUpdateAvailable(remoteVersion, localVersion)
            } else {
                println(ansi().fgGreen().a("OK!").reset())
                println ""
            }
        } catch (Exception e) {
            println("When I was checking for updates to devtool i got the following exception:")
            println(e.getClass().getName() + " - " + e.getMessage())
            println ""
        }
    }

    private void printUpdateAvailable(String remoteVersion, String localVersion) {
        println "\n\n\n"
        println " ----------------------------------------------------- "
        println " ---              " + ansi().fgCyan().a("!CONGRATULATIONS!").reset() + "                --- "
        println " --- " + ansi().fgCyan().a("THERE IS AN AWESOME UPDATE AVAILABLE FOR YOU!").reset() + " --- "
        println " ----------------------------------------------------- "
        println " LOCAL VERSION OF DEVTOOL  : " + localVersion
        println " REMOTE VERSION OF DEVTOOL : " + remoteVersion
        println ""
        println " EXECUTE THE FOLLOWING COMMAND TO UPDATE THE LOCAL VERSION TO THE NEWEST VERSION:"
        println " devtool -install devtool"
        println " ---------------------------------------------------- "
        println ansi().fgYellow().a(" NB Remember to start a new prompt to use the new devtool version!").reset()
        println "\n\n\n"
    }

    String listAndInstallUpdates(boolean installLatest) {
        def output = new StringBuilder()
        def localToolsDir = new File(getToolsDestinationDir())
        localToolsDir.eachDir { localToolnameDir ->
            def localVersion = getLatestVersionForToolFromLocal(getToolsDestinationDir(), localToolnameDir.name)
            def remoteVersion = getLatestVersionForToolFromRemote(localToolnameDir.name)

            if (isRemoteVersionNewest(remoteVersion, localVersion)) {
                if (installLatest) {
                    installTool(localToolnameDir.name, remoteVersion)
                } else {
                    output.append("$localToolnameDir.name $localVersion -> $remoteVersion")
                    output.append('\n')
                }
            }
        }
        return output.toString()
    }

    boolean isRemoteVersionNewest(String remoteVersion, String localVersion) {
        createSortableNumber(remoteVersion) > createSortableNumber(localVersion)
    }

    List<String> getRemoteSortedToolsList() {
        def sortedList = new LinkedList<String>()

        def xmlStream = new URL(getToolsSourceDir()).openStream()
        def nodes = new XmlSlurper().parse(xmlStream)

        nodes.data.children().collect() { contentItem ->
            sortedList.add(contentItem.text.text())
        }

        // we dont want to list the settings and the toolsandversions "tools"
        sortedList.remove("settings")
        sortedList.remove("toolsandversions")

        return sortedList.sort { it.toLowerCase() }
    }

    String listNotInstalled() {
        def output = new StringBuilder()

        for (toolName in getRemoteSortedToolsList()) {
            def installed = isToolInstalled(getToolsDestinationDir(), toolName)

            if (!installed) {
                output.append(toolName)

                def toolVersions = getVersionsForRemoteTool(toolName)

                toolVersions.each { version ->
                    output.append(" $version ")
                }

                output.append('\n')
            }
        }

        return output
    }

    File buildToolsAndVersionsFile() {
        def output = new StringBuilder()
        def allTools = [:]

        // Get tools from standard location
        for (toolName in getRemoteSortedToolsList()) {
            def toolVersions = getVersionsForRemoteTool(toolName)
            allTools[toolName] = toolVersions
        }

        // Get tools from registry
        def registryTools = getToolsFromRegistry()
        registryTools.each { toolData ->
            if (allTools.containsKey(toolData.name)) {
                // Merge versions
                def existingVersions = allTools[toolData.name]
                toolData.versions.each { version ->
                    if (!existingVersions.contains(version)) {
                        existingVersions.add(version)
                    }
                }
            } else {
                // New tool from registry
                allTools[toolData.name] = toolData.versions
            }
        }

        // Sort tools alphabetically and write to output
        allTools.sort().each { toolName, toolVersions ->
            output.append(toolName)
            toolVersions.each { version ->
                output.append(";" + version)
            }
            output.append('\n')
        }

        def tempFile = File.createTempFile("devtool", "txt")
        tempFile.deleteOnExit()
        tempFile.write(output.toString())
        return tempFile
    }

    List getToolsFromRegistry() {
        def registryUrl = "$NEXUS_SERVER_URL/nexus/service/local/repositories/$NEXUS_REPOSITORY/content/devtool/registry"
        def tools = []

        try {
            debugln "Checking for tools in registry: $registryUrl"
            def xml = new XmlSlurper().parse(registryUrl)

            xml.data.'content-item'.each { item ->
                def fileName = item.text.text()
                if (fileName.endsWith('.xml')) {
                    try {
                        def toolName = fileName.replace('.xml', '')
                        def toolRegistryUrl = "$NEXUS_SERVER_URL/nexus/content/repositories/$NEXUS_REPOSITORY/devtool/registry/$fileName"

                        def toolXml = new XmlSlurper().parse(toolRegistryUrl)
                        def versions = []
                        toolXml.versions.version.each { versionNode ->
                            versions.add(versionNode.@number.text())
                        }

                        if (versions.size() > 0) {
                            tools.add([name: toolName, versions: versions])
                            debugln "Found tool in registry: $toolName with ${versions.size()} versions"
                        }
                    } catch (Exception e) {
                        debugln "Error reading registry for ${fileName}: ${e.message}"
                    }
                }
            }
        } catch (FileNotFoundException e) {
            debugln "No registry directory found - this is OK, registry is optional"
        } catch (Exception e) {
            debugln "Error scanning registry: ${e.message}"
        }

        return tools
    }

    void uploadToolsAndVersionsFile(String nexusUserName, String nexusPassword, File buildToolsAndVersionsFile) {
        def process = runCurlCommand("curl -v -u $nexusUserName:$nexusPassword --upload-file $buildToolsAndVersionsFile.absolutePath " + "$NEXUS_SERVER_URL/nexus/content/repositories/$NEXUS_REPOSITORY/devtool/toolsandversions/1.0/toolsandversions-1.0.txt")
        if (process.exitValue() != 0) {
            println "Problem with uploading ToolsAndVersionsFile"
        }

    }

    void deleteOldToolsAndVersionsFile(String nexusUserName, String nexusPassword) {
        def process = runCurlCommand("curl --request DELETE --user \"$nexusUserName:$nexusPassword\" $NEXUS_SERVER_URL/nexus/content/repositories/$NEXUS_REPOSITORY/devtool/toolsandversions/1.0/toolsandversions-1.0.txt")
        if (process.exitValue() != 0) {
            println "Problem with deleting old ToolsAndVersionsFile"
       }
    }

    String listTools() {
        def tempFile = File.createTempFile('devtool', 'txt')
        tempFile.deleteOnExit()

        def process = runCurlCommand("curl $NEXUS_SERVER_URL/nexus/content/repositories/$NEXUS_REPOSITORY/devtool/toolsandversions/1.0/toolsandversions-1.0.txt --output " + tempFile.absolutePath)
        if (process.exitValue() != 0) {
            println "Problem with downloading the list of tools and versions"
            System.exit(0)
        }


        def output = new StringBuilder()

        def paddingSize = 25
        def lineIndex = 0
        def consoleWidth = consoleWidth()

        tempFile.eachLine { line ->
            def toolAndVersionsArray = line.split(';')
            def toolName = toolAndVersionsArray[0]
            String toolText = toolName.padRight(paddingSize, ' -')

            def toolVersions = toolAndVersionsArray.toList().subList(1, toolAndVersionsArray.toList().size())

            def row = new StringBuilder(toolText)
            def longestVersionLength = calculateLongestVersionLength(toolVersions)
            toolVersions.eachWithIndex { version, toolIndex ->
                // pad each version numbers so that they align
                def text = " " + version.padRight(longestVersionLength) + " "
                if (row.length() + text.length() < consoleWidth) {
                    row.append(text)
                } else {
                    if (lineIndex % 2 == 0) {
                        output.append(ansi().fgCyan().a(row).reset())
                    } else {
                        output.append(row)
                    }
                    // start a new row and add padding to align version numbers
                    row = new StringBuilder("\n")
                    row.append(" ".padLeft(paddingSize, ' '))
                }
            }
            // append rest of the row, this will contain contents for the last row
            if (row.toString().trim().length() > 0) {
                if (lineIndex % 2 == 0) {
                    output.append(ansi().fgCyan().a(row).reset())
                } else {
                    output.append(row)
                }
            }
            output.append('\n')
            lineIndex++
        }

        return output
    }

    int calculateLongestVersionLength(List<String> toolVersions) {
        def longestVersionLength = 0
        toolVersions.each { version ->
            if (version.length() > longestVersionLength) {
                longestVersionLength = version.length()
            }
        }
        return longestVersionLength
    }

    int consoleWidth() {
        def columns = bashColumns()
        if (columns == 0) {
            columns = cmdColumns()
        }
        // use some reasonable minimum width. 25 is used padding.
        if (columns < 60) {
            columns = 80
        }
        return columns
    }

    int bashColumns() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("tput", "cols");
            processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
            def process = processBuilder.start()
            def output = process.in.text
            process.destroy()
            if (output?.isInteger()) {
                return output.toInteger()
            }
        } catch (all) {
        }
        return 0
    }

    int cmdColumns() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", "mode con");
            processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
            def process = processBuilder.start()
            def output = process.in.text
            process.destroy()
            // multiline matcher that picks number of columns
            def matcher = (output =~ /(?ms).*Columns:\s+(\d+).*/)
            if (matcher.matches()) {
                return matcher.group(1).toInteger()
            }
        } catch (all) {
        }
        return 0
    }

    /**
     * if toolname and/or version not exist - error <p/>
     * if tool and version exist - delete <p/>

     * when delete: <p/>
     * delete from filesystem <p/>
     * if tool and version in userpath - delete from userpath and delete devtool_toolname <p/>
     * if tool has only this version - delete variable devtool_toolname <p/>
     */
    void uninstallTool(String toolName, String toolVersion) {
        debugln "uninstall $toolName $toolVersion"
        String toolDir = getToolsDestinationDir() + sepChar + toolName + sepChar + toolVersion

        def toolDirFile = new File(toolDir)
        if (!toolDirFile.exists()) {
            println "Toolname and/or version is not correct. Could not find: $toolDir"
            System.exit(0)
        }

        def deleted = toolDirFile.deleteDir()
        if (!deleted) {
            println "Was not able to delete: $toolDir"
            System.exit(0)
        }

        def userPath = getUserDefinedVariable("path")
        if (userPath.contains(toolDir)) {
            removeToolpathFromWindowsUserpath(userPath, toolName)
            deleteDevtoolVariable(toolName)
        }

        // cleanup devtool variable if no more versions are installed
        if (getNumberOfInstalledVersionsForTool(toolName) == 0) {
            deleteDevtoolVariable(toolName)
        }
    }

    private int getNumberOfInstalledVersionsForTool(String toolName) {
        new File(getToolsDestinationDir() + sepChar + toolName).listFiles(new FileFilter() {
            @Override
            boolean accept(File pathname) {
                pathname.isDirectory()
            }
        }).toList().size()
    }

    private void removeToolpathFromWindowsUserpath(String userPath, String toolName) {
        debugln "addToolDependentPaths -> userPath before remove= $userPath"
        def cleanedUserPath = removeOldToolPaths(toolName, userPath)
        debugln "addToolDependentPaths -> userPath after remove= $cleanedUserPath"

        def setxCommand = "cmd /c setx path " + cleanedUserPath
        debugln "addToolDependentPaths -> setxCommand = $setxCommand"
        Process addToolPathProcess = setxCommand.execute()
        debugln "addToolDependentPaths result: ${addToolPathProcess.text}"
    }

    private void deleteDevtoolVariable(String toolName) {
        def setxTool = "cmd /c setx devtool_$toolName \"\""
        debugln "createUserToolVariable setxTool = $setxTool"
        Process deleteDevtoolVariableProcess = setxTool.execute()
        debugln "createUserToolVariable result: ${deleteDevtoolVariableProcess.text}"
    }

    void installTool(String toolName, String toolVersion) {
        if (toolVersion != null) {
            if (toolVersion == "--") {
                println "Version to install not entered"
                System.exit(0)
            }
        } else {
            toolVersion = getLatestVersionForToolFromRemote(toolName)
        }

        String sourceDir = getToolsSourceDir() + "/" + toolName + "/" + toolVersion
        String destDir = getToolsDestinationDir() + sepChar + toolName

        def ant = new AntBuilder()

        def toolnameVersionZip = toolName + "-" + toolVersion + ".zip"
        def remoteZipFile = sourceDir + "/" + toolnameVersionZip

        // Check if tool is in registry first
        def registryData = getToolFromRegistry(toolName, toolVersion)
        if (registryData) {
            println "Tool found in registry, downloading from: ${registryData.nexusUrl}"
            remoteZipFile = registryData.nexusUrl
        } else {
            // Standard location - validate it exists
            validateDirs(sourceDir, getToolsDestinationDir())
        }

        def localZipFile = destDir + sepChar + toolnameVersionZip

        new File(destDir).mkdirs()
        def zipDist = destDir + sepChar + toolnameVersionZip
        copyFile(toolName + "-" + toolVersion, remoteZipFile, zipDist)

        // Unzip and reorganize if needed
        def tempUnzipDir = destDir + sepChar + "temp_unzip"
        ant.unzip(src: localZipFile, dest: tempUnzipDir, overwrite: "true")
        sleep(250)

        // Smart reorganization - ensure tool is in version subfolder
        reorganizeToolStructure(tempUnzipDir, destDir, toolVersion, registryData)

        // Cleanup
        ant.delete(file: localZipFile, quiet: true)
        ant.delete(dir: tempUnzipDir, quiet: true)

        setToolPathAndVariables(toolName, toolVersion)
    }

    void reorganizeToolStructure(String tempDir, String destDir, String toolVersion, Map registryData) {
        def tempDirFile = new File(tempDir)
        def targetVersionDir = new File(destDir + sepChar + toolVersion)

        if (!tempDirFile.exists()) {
            println "Error: temp unzip directory not found"
            return
        }

        def entries = tempDirFile.listFiles()
        if (entries == null || entries.length == 0) {
            println "Warning: ZIP appears to be empty"
            return
        }

        // Check if already in correct format (single folder named after version)
        if (entries.length == 1 && entries[0].isDirectory() && entries[0].name == toolVersion) {
            debugln "ZIP already in correct format, moving directly"
            def ant = new AntBuilder()
            ant.move(file: entries[0].absolutePath, tofile: targetVersionDir.absolutePath)
            return
        }

        // Check if there's a single root folder (common pattern)
        if (entries.length == 1 && entries[0].isDirectory()) {
            debugln "ZIP has single root folder, reorganizing"
            def ant = new AntBuilder()
            targetVersionDir.mkdirs()

            // Move contents of the single folder to version folder
            def innerFiles = entries[0].listFiles()
            if (innerFiles != null) {
                innerFiles.each { file ->
                    ant.move(file: file.absolutePath, todir: targetVersionDir.absolutePath)
                }
            }
            return
        }

        // Multiple files/folders in root or flat structure - move everything
        debugln "ZIP has multiple items in root, moving all to version folder"
        targetVersionDir.mkdirs()
        def ant = new AntBuilder()
        entries.each { file ->
            ant.move(file: file.absolutePath, todir: targetVersionDir.absolutePath)
        }
    }

    void setupTool(String toolName, String version) {
        if (toolName == null || version == null) {
            println "Both toolname and version has to be specified"
            System.exit(-1)
        }

        verifyThatToolIsInstalledOtherwiseExit(toolName, version)

        setToolPathAndVariables(toolName, version)
    }

    private void verifyThatToolIsInstalledOtherwiseExit(String toolName, String version) {
        String destDir = getToolsDestinationDir() + sepChar + toolName + sepChar + version
        if (!new File(destDir).exists()) {
            println "Tool $toolName with version $version is not installed"
            System.exit(-1)
        }
    }

    String getToolRegistryUrl(String toolName) {
        def registryUrl = "$NEXUS_SERVER_URL/nexus/content/repositories/$NEXUS_REPOSITORY/devtool/registry/${toolName}.xml"
        debugln "Checking for registry at: $registryUrl"
        try {
            def conn = new URL(registryUrl).openConnection()
            conn.setConnectTimeout(5000)
            conn.setReadTimeout(5000)
            conn.connect()
            conn.getInputStream().close()
            debugln "Registry found for $toolName"
            return registryUrl
        } catch (FileNotFoundException e) {
            debugln "No registry found for $toolName"
            return null
        } catch (Exception e) {
            debugln "Error checking registry for $toolName: ${e.message}"
            return null
        }
    }

    Map getToolFromRegistry(String toolName, String toolVersion) {
        def registryUrl = getToolRegistryUrl(toolName)
        if (!registryUrl) {
            return null
        }

        try {
            def registry = new XmlSlurper().parse(registryUrl)
            def versionNode = registry.versions.version.find { it.@number.text() == toolVersion }

            if (versionNode) {
                def result = [:]
                result.nexusUrl = versionNode.nexusUrl.text()
                result.zipStructure = [:]

                if (versionNode.zipStructure.size() > 0) {
                    result.zipStructure.rootFolder = versionNode.zipStructure.rootFolder.text()
                    result.zipStructure.flatRoot = versionNode.zipStructure.flatRoot.text()
                }

                debugln "Found tool in registry: ${result.nexusUrl}"
                return result
            }
        } catch (Exception e) {
            debugln "Error reading registry for $toolName: ${e.message}"
        }

        return null
    }

    void copyFile(String toolNameAndVersion, String src, String dest) {
        def startTime = System.currentTimeMillis()

        def destFile = new FileOutputStream(dest)

        HttpURLConnection conn = new URL(src).openConnection() as HttpURLConnection
        conn.setRequestMethod("HEAD")
        conn.getInputStream()
        long fileSize = conn.getContentLength()
        conn.disconnect()

        def srcStream = new URL(src).openStream()

        byte[] buf = new byte[1024 * 10]
        int read
        long processedBytes = 0
        while ((read = srcStream.read(buf)) >= 0) {
            destFile.write(buf, 0, read)
            processedBytes += read
            print "\rDownloading $toolNameAndVersion from repository: " + String.format("%.0f", (processedBytes * 100) / fileSize) + "%"
        }
        def downloadTimeInSecs = (System.currentTimeMillis() - startTime) / 1000
        println "\nDownload time: " + downloadTimeInSecs + " sec - " + ((fileSize / 1024) / downloadTimeInSecs).longValue() + " Kb/sec"
    }

    void setToolPathAndVariables(String toolName, String toolVersion) {
        createUserToolVariable(toolName, toolVersion)

        ToolSettings settings = downloadSettingsForTool(toolName)
        debugln "Settings: $settings"

        addToolDependentPaths(toolName, toolVersion, settings)
        addCustomToolVariables(toolName, toolVersion, settings)

        println settings.comment
    }

    ToolSettings downloadSettingsForTool(String toolName) {
        def settingsStream
        try {
            settingsStream = new URL(getToolsSourceDir() + "/settings/$toolName/1/$toolName-1.xml").openStream()
        } catch (FileNotFoundException ignore) {
            debugln "No settings found for tool: $toolName"
            return new ToolSettings()
        }

        def settings = new ToolSettings()

        def nodes = new XmlSlurper().parse(settingsStream)
        nodes.paths.path.collect().each { path ->
            debugln "adding settings path: " + path.text()
            settings.getPaths().add(path.text())
        }

        nodes.toolvars.toolvar.collect().each { toolVar ->
            debugln "adding settings toolVar: " + toolVar.text()
            settings.getToolVariables().add(toolVar.text())
        }

        settings.setComment(nodes.comment.text())

        return settings
    }

    void addToolDependentPaths(String toolName, String toolVersion, ToolSettings settings) {
        settings.getPaths().each { pathToAdd ->
            def userPath = getUserDefinedVariable("path")

            // remove old path
            userPath = removeOldToolPaths(toolName, userPath)
            debugln "addToolDependentPaths -> userPath after remove= $userPath"

            def setxCommand = "cmd /c setx path " + userPath + pathSepChar + getToolsDestinationDir() + sepChar + toolName + sepChar + toolVersion + sepChar + pathToAdd + ";"
            debugln "addToolDependentPaths -> setxCommand = $setxCommand"
            Process p = setxCommand.execute()
            debugln "addToolDependentPaths result: ${p.text}"
        }
    }

    void addCustomToolVariables(String toolName, String toolVersion, ToolSettings settings) {
        settings.getToolVariables().each { pathToAdd ->
            def toolPath = getToolsDestinationDir() + sepChar + toolName + sepChar + toolVersion
            def setxCommand = "cmd /c setx " + pathToAdd + " " + toolPath
            debugln "addCustomToolVariables -> setxCommand = $setxCommand"
            Process p = setxCommand.execute()
            debugln "addCustomToolVariables result: ${p.text}"
        }
    }

    String removeOldToolPaths(String toolName, String userPath) {
        def toolPath = getToolsDestinationDir() + sepChar + toolName + sepChar
        debugln "removeOldToolPaths toolpath: " + toolPath
        return removeDirectoryFromPaths(toolPath, userPath)
    }

    String removeDirectoryFromPaths(String toolPath, String userPath) {
        def toolPathEscaped = toolPath.replace("\\", "\\\\") // if on windows
        def regEx = "" + pathSepChar + toolPathEscaped + "[\\d\\.\\w\\\\]*"
        debugln "removeOldToolPaths regEx: " + regEx
        userPath = userPath.replaceAll(regEx, "") // remove all path instances of the tool
        userPath = userPath.replaceAll("" + pathSepChar + pathSepChar, "" + pathSepChar)
        // remove all double pathSepChar with a single one
        return userPath
    }

    void createUserToolVariable(String toolName, String version) {
        def toolPath = getToolsDestinationDir() + sepChar + toolName + sepChar + version
        def setxTool = "cmd /c setx devtool_$toolName " + toolPath
        debugln "createUserToolVariable setxTool = $setxTool"
        Process p = setxTool.execute()
        debugln "createUserToolVariable result: ${p.text}"
    }

    def validateDirs(String sourceDir, String destDir) {
        if (new URL(sourceDir).openStream() == null) {
            println "Sourcedir: " + sourceDir + " does not exist."
            System.exit(-1)
        }
        if (!new File(destDir).exists()) {
            println "Destinationdir: " + destDir + " does not exist."
            System.exit(-1)
        }
    }

    /**
     * Return the value registered on the machine for the given variable name
     * On windows the registry is used
     */
    String getUserDefinedVariable(String variableName) {
        Process regValue = "reg query HKCU\\Environment /v $variableName".execute()
        def outputText = regValue.text
        def regValueIndex
        if (outputText != null && outputText.trim().length() > 0) {
            regValueIndex = outputText.indexOf('_SZ') + 6
        } else {
            return ""
        }

        return outputText.substring(regValueIndex).trim()
    }

    boolean isToolInstalled(String toolsDir, String toolName) {
        def toolDir = new File("$toolsDir/$toolName")
        def toolVersionList = toolDir.list(getVersionFilenameFilter())
        if (!toolDir.exists() || toolVersionList.length == 0) {
            return false
        }

        return true
    }

    String getLatestVersionForToolFromLocal(String toolsDir, String toolName) {
        def toolDir = new File("$toolsDir/$toolName")
        def toolVersionList = toolDir.list(getVersionFilenameFilter())
        if (!toolDir.exists() || toolVersionList.length == 0) {
            return "0"
        }

        return sortVersionNumbers(Arrays.asList(toolVersionList)).last()
    }

    String getLatestVersionForToolFromRemote(String toolName) {
        getVersionsForRemoteTool(toolName).last()
    }

    List<String> getVersionsForRemoteTool(String toolName) {
        def versionList = new LinkedList<String>()

        def versionsXml
        try {
            versionsXml = new URL(getToolsSourceDir() + "/" + toolName).openStream()
        } catch (FileNotFoundException ignore) {
            versionList.add("0")
            return versionList
        }
        def versions = new XmlSlurper().parse(versionsXml)
        versions.data.children().collect() { versionsContentItem ->
            // ignore files like: maven-metadata.xml and maven-metadata.xml.md5
            if (versionsContentItem.text.text().indexOf("maven") == -1) {
                versionList.add(versionsContentItem.text.text())
            }
        }
        return sortVersionNumbers(versionList)
    }

    protected List<String> sortVersionNumbers(List<String> versionList) {
        return versionList.sort { x, y ->
            createSortableNumber(x) <=> createSortableNumber(y)
        }
    }

    FilenameFilter getVersionFilenameFilter() {
        new FilenameFilter() {
            @Override
            boolean accept(File dir, String name) {
                // Match version numbers like: 1, 1.0, 1.2.3, 11.0.2, etc.
                return name.matches("\\d+(\\.\\d+)*")
            }
        }
    }

    boolean zipped_tool_have_version_folder(String zippedToolPath) {
        ZipFile zipFile = new ZipFile(zippedToolPath)
        def entry = zipFile.getEntry(extractToolVersion(zippedToolPath))
        if (entry == null) {
            return false
        }
        return true
    }

    boolean zipped_tool_have_version_folder_only(String zippedToolPath) {
        ZipFile zipFile = new ZipFile(zippedToolPath)
        def entries = zipFile.entries()
        boolean result = true
        entries.each { entry ->
            if (entry.name.indexOf("/") == -1) {
                result = false
            }
        }
        return result
    }

    boolean zipped_tool_version_does_not_match(String zippedToolPath) {
        return zipped_tool_have_version_folder(zippedToolPath)
    }

    long createSortableNumber(String versionString) {
        def split = versionString.split('\\.')
        long result = 0
        if (split.length > 3) {
            result += Long.parseLong(split[3])
        }
        if (split.length > 2) {
            result += Long.parseLong(split[2]) * 100L
        }
        if (split.length > 1) {
            result += Long.parseLong(split[1]) * 1000000L
        }

        result += Long.parseLong(split[0]) * 1000000000L

        return result
    }

    String getToolsSourceDir() {
        return "$NEXUS_SERVER_URL/nexus/service/local/repositories/$NEXUS_REPOSITORY/content/devtool"
    }

    String getToolsDestinationDir() {
        return getUserDefinedVariable("devtool_tools")
    }

    def debugln(String debugString) {
        if (debugOutput) {
            println "DEBUG: " + debugString
        }
    }
}
