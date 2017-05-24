#build:

###install raml-cop
1. `brew install node`
2. `npm install raml-cop`

###install jekyll
1. `brew install rbenv`
2. `rbenv install -l`
3. `rbenv install 2.4.1`
4. `rbenv local 2.4.1`
5. `gem install bundler`
6. `bundle install --path vendor/bundle`
7. `bundle exec jekyll serve`

#deploy:

###package
0. `./scripts/local_generate_javadoc_for_web_site.sh`
1. `cd jekyll-www.mock-server.com`
2. `bundle exec jekyll serve`
3. `cd _site`
4. `zip -r mockserver_site.zip .`

###upload
1. upload to [aws](https://console.aws.amazon.com/quickstart-website/website/aws-website-mockserver-nb9hq)
