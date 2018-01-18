#build:

###install raml-cop
    brew install node
    npm install raml-cop

###install jekyll
    brew install rbenv
    rbenv install -l
    rbenv install 2.4.3
    rbenv local 2.4.3
    `rbenv which gem` install bundler
    bundle install --path vendor/bundle
    bundle exec jekyll serve

#deploy:

###package
	./scripts/local_generate_javadoc_for_web_site.sh
	cd jekyll-www.mock-server.com
	bundle exec jekyll build
	cd _site
	zip -r mockserver_site.zip .

###upload
upload to [aws](https://console.aws.amazon.com/quickstart-website/website/aws-website-mockserver-nb9hq)
