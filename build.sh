#!/bin/bash

# Define directories
SRC_DIR="src"
BUILD_DIR="build"
MAIN_CLASS="dev.hawala.dmachine.DwarfMain"
JAR_NAME="dwarf.jar"

# Create build directory if it doesn't exist
mkdir -p $BUILD_DIR

# Compile the Java files
echo "Compiling Java files..."
find $SRC_DIR -name "unittest" -prune -o -name "*.java" -print > sources.txt
javac -d $BUILD_DIR @sources.txt

# Check if compilation was successful (any errors)
if [ $? -ne 0 ]; then
    echo "Compilation failed due to errors."
    rm sources.txt
    exit 1
fi

rm sources.txt
echo "Compilation successful."

# Create a manifest file
echo "Creating manifest..."
MANIFEST_FILE=$BUILD_DIR/MANIFEST.MF
echo "Manifest-Version: 1.0" > $MANIFEST_FILE
echo "Main-Class: $MAIN_CLASS" >> $MANIFEST_FILE

# Create the JAR file
echo "Creating JAR..."
# Add 'v' to 'cfm' to get verbose output
jar cfm $BUILD_DIR/$JAR_NAME $MANIFEST_FILE -C $BUILD_DIR .

# Check if the JAR creation was successful
if [ $? -ne 0 ]; then
    echo "JAR creation failed."
    exit 1
fi

echo "JAR created successfully: $BUILD_DIR/$JAR_NAME"

# Clean up manifest file
rm $MANIFEST_FILE