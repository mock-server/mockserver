def deleteFile(file) {
    if (file.exists()) {
        return file.delete()
    } else {
        return true
    }
}

"pkill -INT -f jetty".execute().waitFor()

return deleteFile(new File(basedir as String, "mockserver.log" as String))
