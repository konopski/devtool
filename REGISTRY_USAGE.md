# Tool Registry System - Usage Guide

## Overview

The registry system allows you to register tools from existing Nexus locations without copying/uploading files. Tools stay where they are, and devtool just knows where to find them.

## Benefits

- **Zero duplication** - Files remain in their original locations
- **Team autonomy** - Different teams can manage their own repos
- **Centralized discovery** - Users discover all tools through devtool
- **Flexible naming** - Supports tools with multiple hyphens (e.g., `apache-maven-3.8.6.zip`)
- **Flexible ZIP structure** - Accepts various ZIP formats, reorganizes automatically

## How to Register a Tool

### Step 1: Create Registry XML

Create an XML file describing where your tool lives in Nexus:

**keystoreexplorer-registry.xml:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<tool>
  <name>keystoreexplorer</name>
  <versions>
    <version number="5.5.2">
      <nexusUrl>https://nexus.yourcompany.com/repos/security-team/tools/kse-5.5.2.zip</nexusUrl>
    </version>
    <version number="5.4.0">
      <nexusUrl>https://nexus.yourcompany.com/repos/security-team/tools/kse-5.4.0.zip</nexusUrl>
    </version>
  </versions>
</tool>
```

**Optional - Specify ZIP structure hints:**
```xml
<version number="5.5.2">
  <nexusUrl>https://nexus.yourcompany.com/repos/tools/kse-5.5.2.zip</nexusUrl>
  <zipStructure>
    <rootFolder>kse-5.5.2</rootFolder>
    <flatRoot>false</flatRoot>
  </zipStructure>
</version>
```

### Step 2: Register the Tool

```bash
devtool -register keystoreexplorer-registry.xml
```

You'll be prompted for:
- Nexus credentials (to upload registry XML)
- Confluence credentials (to create announcement blog post)

The tool will:
1. Upload the registry XML to Nexus (`devtool/registry/` folder)
2. Regenerate the tool index
3. Create a Confluence blog post announcing the new tool

### Step 3: Users Install Normally

Users don't need to know about the registry - they just use devtool normally:

```bash
devtool -list
# Shows all tools including registered ones

devtool -install keystoreexplorer
# Downloads from the URL specified in registry
```

## Registry XML Schema

### Required Elements

```xml
<tool>
  <name>toolname</name>              <!-- Tool identifier, no spaces -->
  <versions>
    <version number="X.Y.Z">         <!-- Semantic version -->
      <nexusUrl>...</nexusUrl>       <!-- Full URL to ZIP file -->
    </version>
  </versions>
</tool>
```

### Optional Elements

```xml
<zipStructure>
  <rootFolder>foldername</rootFolder>  <!-- If ZIP has single root folder -->
  <flatRoot>true|false</flatRoot>      <!-- If files are directly in ZIP root -->
</zipStructure>
```

## Updating a Tool

To add new versions or update URLs:

1. Edit your registry XML file
2. Run `devtool -register toolname-registry.xml` again
3. The registry will be overwritten with new information

## Advanced: External URLs

You can even point to external repositories:

```xml
<version number="3.8.6">
  <nexusUrl>https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.8.6/apache-maven-3.8.6-bin.zip</nexusUrl>
</version>
```

## Supported ZIP Formats

The registry system accepts various ZIP structures:

### Format 1: Strict devtool format
```
toolname-1.0.zip
└── 1.0/
    ├── bin/
    └── lib/
```

### Format 2: Single root folder
```
apache-maven-3.8.6.zip
└── apache-maven-3.8.6/
    ├── bin/
    └── lib/
```
*Auto-reorganized during installation*

### Format 3: Flat structure
```
kse-5.5.2.zip
├── kse.exe
├── lib/
└── icons/
```
*Auto-reorganized during installation*

## Naming Conventions

### Tool Names
- Use lowercase
- Hyphens are allowed (e.g., `apache-maven`, `keystore-explorer`)
- No spaces

### File Names
Now supports multiple hyphens:
- ✅ `apache-maven-3.8.6.zip`
- ✅ `keystore-explorer-5.5.2.zip`
- ✅ `openjdk-11.0.2-jre.zip`

## Example Workflow

**Security Team** manages KeyStore Explorer in `nexus.company.com/repos/security-tools/`:
```bash
# They have: kse-5.5.2.zip

# Create registry:
cat > kse-registry.xml <<EOF
<tool>
  <name>keystoreexplorer</name>
  <versions>
    <version number="5.5.2">
      <nexusUrl>https://nexus.company.com/repos/security-tools/kse-5.5.2.zip</nexusUrl>
    </version>
  </versions>
</tool>
EOF

# Register:
devtool -register kse-registry.xml
```

**Infrastructure Team** manages Cmder in `nexus.company.com/repos/infra/utils/`:
```bash
# They have: cmder_mini.zip (no version in name!)

# Create registry (assign version):
cat > cmder-registry.xml <<EOF
<tool>
  <name>cmder</name>
  <versions>
    <version number="1.3.20">
      <nexusUrl>https://nexus.company.com/repos/infra/utils/cmder_mini.zip</nexusUrl>
    </version>
  </versions>
</tool>
EOF

# Register:
devtool -register cmder-registry.xml
```

**Developers** just use devtool:
```bash
devtool -list
# Shows both keystoreexplorer and cmder

devtool -install keystoreexplorer
devtool -install cmder
```

## Troubleshooting

### Registry not found during install
- Check if registry XML was uploaded: `https://nexus.../devtool/registry/toolname.xml`
- Try with `-debug` flag: `devtool -debug -install toolname`

### Tool index not updated
- Re-run register: `devtool -register toolname-registry.xml`
- Index is at: `https://nexus.../devtool/toolsandversions/1.0/toolsandversions-1.0.txt`

### ZIP structure issues
- Devtool will auto-reorganize most formats
- Check debug output during install: `devtool -debug -install toolname`

## Backward Compatibility

- Old-style tools (uploaded via `-upload`) still work
- Registry tools and standard tools can coexist
- No changes needed for existing deployments
