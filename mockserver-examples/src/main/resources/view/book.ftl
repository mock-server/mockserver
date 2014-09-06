<#ftl strip_whitespace=true strict_syntax=true strip_text=true>
<#-- @ftlvariable name="book" type="org.mockserver.model.Book" -->
<#setting url_escaping_charset="UTF-8">

<@compress single_line=true>
    <#escape x as x?html>
	<!DOCTYPE html>
	<html lang="en_GB">
		<head>
			<title>List of Books</title>
			<meta name="description" content="List of Books"/>
			<link rel="stylesheet" type="text/css" href="example.css">
		</head>
        <#flush>
		<body onunload="">
			<p id="id"><strong>Id:</strong><a href="/book/${book.id}">${book.id}</a></p>

			<p id="title"><strong>Title:</strong>${book.title}</p>

			<p id="author"><strong>Author:</strong>${book.author}</p>

			<p id="publicationDate"><strong>Publication Date:</strong>${book.publicationDate}</p>

			<p id="isbn"><strong>ISBN:</strong>${book.isbn}</p>
		</body>
	</html>
    </#escape>
</@compress>