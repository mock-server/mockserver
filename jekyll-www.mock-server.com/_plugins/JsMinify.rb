module Jekyll
  $minified_filename = ''

  # use this as a workaround for getting cleaned up
  # reference: https://gist.github.com/920651
  class JsMinifyFile < StaticFile
    def write(dest)
      # do nothing
    end
  end

  # minify js files
  class JsMinifyGenerator < Generator
    safe true

    def generate(site)
      config = Jekyll::JsMinifyGenerator.get_config

      files_to_minify = config['files'] || get_js_files(site, config['js_source'])

      last_modified = files_to_minify.reduce( Time.at(0) ) do |latest,filepath|
        modified = File.mtime(filepath)
        modified > latest ? modified : latest
      end
      # reset the minified filename
      $minified_filename = last_modified.strftime("%Y%m%d%H%M") + '.min.js'

      output_dir = File.join(site.config['destination'], config['js_destination'])
      output_file = File.join(output_dir, $minified_filename)

      # need to create destination dir if it doesn't exist
      FileUtils.mkdir_p(output_dir)
      minify_js(files_to_minify, output_file)
      site.static_files << JsMinifyFile.new(site, site.source, config['js_destination'], $minified_filename)
    end

    # read the js dir for the js files to compile
    def get_js_files(site, relative_dir)
      # not sure if we need to do this, but keep track of the current dir
      pwd = Dir.pwd
      Dir.chdir(File.join(site.config['source'], relative_dir))
      # read js files
      js_files = Dir.glob('*.js').map{ |f| File.join(relative_dir, f) }
      Dir.chdir(pwd)

      return js_files
    end

    def minify_js(js_files, output_file)
      js_files = js_files.join(' ')
      juice_cmd = "juicer merge -f #{js_files} -o #{output_file}"
      puts juice_cmd
      system(juice_cmd)
    end

    # Load configuration from JsMinify.yml
    def self.get_config
      if @config == nil
        @config = {
          'js_source' => 'scripts', # relative to the route
          'js_destination' => '/scripts' # relative to site.config['destination']
        }
        config = YAML.load_file('JsMinify.yml') rescue nil
        if config.is_a?(Hash)
          @config = @config.merge(config)
        end
      end

      return @config
    end
  end

  class JsMinifyLinkTag < Liquid::Tag
    def initialize(tag_name, text, tokens)
      super
    end

    def render(context)
      config = Jekyll::JsMinifyGenerator.get_config
      File.join(config['js_destination'], $minified_filename)
    end
  end
end

Liquid::Template.register_tag('minified_js_file', Jekyll::JsMinifyLinkTag)
