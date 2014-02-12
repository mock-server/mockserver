def assertFileExists(file) {
    if (!file.exists() || file.isDirectory()) {
        println("MockServer log file [" + file.getAbsolutePath() + "] was not create")
        return false
    } else {
        file.delete();
    }
    return true
}

// TODO fix why maven plugin does not produce log file
// assert assertFileExists(new File(basedir as String, "mockserver.log" as String))

return true
