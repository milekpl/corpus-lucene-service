Set WshShell = CreateObject("WScript.Shell")
WshShell.CurrentDirectory = "D:\git\corpus-lucene-service"
WshShell.Run "cmd /c java -Xmx4g -jar target\corpus-lucene-service-1.1.0-SNAPSHOT.jar serve --index D:\Dokumenty\slownik-wielki\lucene\lucene-corpus.index --port 8082", 0, True
