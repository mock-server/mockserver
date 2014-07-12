def deleteFile(file) {
    if (file.exists()) {
        return file.delete()
    } else {
        return true
    }
}

return deleteFile(new File(basedir as String, "mockserver.log" as String))
