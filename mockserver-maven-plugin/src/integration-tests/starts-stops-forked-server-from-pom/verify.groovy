def assertFileExists(file) {
    if (!file.exists() || file.isDirectory()) {
        println("Mock Server log file [" + file.getAbsolutePath() + "] was not create")
        return false
    } else {
        file.delete();
    }
    return true
}

assert assertFileExists(new File(basedir as String, "mockserver.log" as String))

return true
