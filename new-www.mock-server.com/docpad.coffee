# The DocPad Configuration File
# It is simply a CoffeeScript Object which is parsed by CSON
docpadConfig = {

	# =================================
	# Template Data
	# These are variables that will be accessible via our templates
	# To access one of these within our templates, refer to the FAQ: https://github.com/bevry/docpad/wiki/FAQ

	templateData:

		# Specify some site properties
		site:
			# The production url of our website
			url: "http://www.mock-server.com"

			# Here are some old site urls that you would like to redirect from
			oldUrls: [
				'www.mock-server.com'
			]

			# The default title of our website
			title: "Mock Server"

			# The website description (for SEO)
			description: """
				An API to easily mock any system or service you integrate with via HTTP or HTTPS from either Java or JavaScript.
				"""

			# The website keywords (for SEO) separated by commas
			keywords: """
				mockserver, mock server, mock http server, http mock server, mock-server, mock rest service, mock server java, mock api, java mock http server, server mock, java mock server, mock rest server, java mock web service, api mock, mock web service java, mock web server, mock service, mock rest api, mock http response, mock http server java, mock api server, json mock service, api mocking
				"""

			# The website author's name
			author: "James D Bloom"

			# The website author's email
			email: "your@email.com"

			# Styles
			styles: [
				"http://yui.yahooapis.com/pure/0.5.0/pure-min.css"
        "//fonts.googleapis.com/css?family=Indie+Flower|Gloria+Hallelujah|Lato|Averia+Sans+Libre:300normal,300italic,400normal,400italic,700normal,700italic|Amatic+SC:400normal|Belleza:400normal|Belgrano:400normal|Open+Sans:400normal|Dosis:400normal|Codystar:400normal|Concert+One:400normal|Oswald:400normal|Economica:400normal|Duru+Sans:400normal&amp;subset=all"
#        "//fonts.googleapis.com/css?family=Rock+Salt|Lato|Averia+Sans+Libre:300normal,300italic,400normal,400italic,700normal,700italic|Amatic+SC:400normal|Belleza:400normal|Belgrano:400normal|Open+Sans:400normal|Dosis:400normal|Codystar:400normal|Concert+One:400normal|Oswald:400normal|Economica:400normal|Duru+Sans:400normal&amp;subset=all"
				"/styles/style.css"
			]

			# Scripts
			scripts: [
        "/scripts/toggle_menu.js"
        "/scripts/google_analytics.js"
      ]


		# -----------------------------
		# Helper Functions

		# Get the prepared site/document title
		# Often we would like to specify particular formatting to our page's title
		# we can apply that formatting here
		getPreparedTitle: ->
			# if we have a document title, then we should use that and suffix the site's title onto it
			if @document.title
				"#{@document.title} | #{@site.title}"
			# if our document does not have it's own title, then we should just use the site's title
			else
				@site.title

		# Get the prepared site/document description
		getPreparedDescription: ->
			# if we have a document description, then we should use that, otherwise use the site's description
			@document.description or @site.description

		# Get the prepared site/document keywords
		getPreparedKeywords: ->
			# Merge the document keywords with the site keywords
			@site.keywords.concat(@document.keywords or []).join(', ')


	# =================================
	# Collections
	# These are special collections that our website makes available to us

	collections:
		pages: (database) ->
			database.findAllLive({pageOrder: $exists: true}, [pageOrder:1,title:1])

	# =================================
	# DocPad Events

	# Here we can define handlers for events that DocPad fires
	# You can find a full listing of events on the DocPad Wiki
	events:

		# Server Extend
		# Used to add our own custom routes to the server before the docpad routes are added
		serverExtend: (opts) ->
			# Extract the server from the options
			{server} = opts
			docpad = @docpad

			# As we are now running in an event,
			# ensure we are using the latest copy of the docpad configuraiton
			# and fetch our urls from it
			latestConfig = docpad.getConfig()
			oldUrls = latestConfig.templateData.site.oldUrls or []
			newUrl = latestConfig.templateData.site.url

			# Redirect any requests accessing one of our sites oldUrls to the new site url
			server.use (req,res,next) ->
				if req.headers.host in oldUrls
					res.redirect(newUrl+req.url, 301)
				else
					next()

  plugins:
    # build a sitemap file
    sitemap:
      cachetime: 600000
      changefreq: 'weekly'
      priority: 0.5
      filePath: 'sitemap.xml'
    # enable rss feed readers
    rss:
      default:
        collection: 'pages'
        url: '/rss.xml' # optional, this is the default

}


# Export our DocPad Configuration
module.exports = docpadConfig
