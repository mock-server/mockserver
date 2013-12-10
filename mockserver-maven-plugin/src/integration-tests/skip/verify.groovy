def assertFileDoesNotExist(file) {
    if (file.exists()) {
        println("MockServer log file [" + file.getAbsolutePath() + "] was create")
        file.delete();
        return false
    }
    return true
}

assert assertFileDoesNotExist(new File(basedir as String, "mockserver.log" as String))

return true