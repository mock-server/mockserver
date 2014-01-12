<#ftl strip_whitespace=true strict_syntax=true strip_text=true>
<#-- @ftlvariable name="books" type="org.mockserver.model.Book[]" -->
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
			<table>
				<tbody>
				<tr>
					<th>Details</th>
					<th>Title</th>
					<th>Author</th>
				</tr>
                    <#list books as book>
					<tr id="${book.id}">
						<td class="id"><a href="/book/${book.id}">${book.id}</a></td>
						<td class="title">${book.title}</td>
						<td class="author">${book.author}</td>
					</tr>
                    </#list>
				</tbody>
			</table>
		</body>
	</html>
    </#escape>
</@compress>