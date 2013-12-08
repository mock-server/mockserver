def assertFileDoesNotExist(file) {
    if (file.exists()) {
        println("Mock Server log file [" + file.getAbsolutePath() + "] was create")
        file.delete();
        return false
    }
    return true
}

assert assertFileDoesNotExist(new File(basedir as String, "mockserver.log" as String))

return true