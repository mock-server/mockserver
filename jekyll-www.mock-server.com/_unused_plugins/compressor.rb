# 
# File compressor plugin for jekyll
# =================================
# 
# By [mytharcher](https://github.com/mytharcher)
# 2012-05-20
# 
# Updated by [nicoespeon](https://github.com/nicoespeon)
# 2013-04-12
# 
# This plugin for compressing text files, including
# HTML and JavaScript of jekyll site. NOT support CSS
# file yet.
# 
# The JavaScript compressing requires 'packr', a
# third-party lib. I'm not sure if GitHub has this
# extension. But the compressor for HTML files works OK.
# 
# For HTML files it uses Alan Moore's regexp : 
# http://stackoverflow.com/questions/5312349/minifying-final-html-output-using-regular-expressions-with-codeigniter
# It removes spaces between HTML, excepted within
# <textarea> and <pre> code, so you don't get into trouble!
# 

require 'packr'

module Jekyll

  module Compressor

    def compress_html(content)
      content.gsub(/(?>[^\S ]\s*|\s{2,})(?=(?:(?:[^<]++|<(?!\/?(?:textarea|pre)\b))*+)(?:<(?>textarea|pre)\b|\z))/ix, '')
    end

    # Really writing process
    def output_file(dest, content)
      FileUtils.mkdir_p(File.dirname(dest))
      File.open(dest, 'w') do |f|
        f.write(content)
      end
    end

    def output_html(dest, content)
      path = self.destination(dest)
      if File.extname(path) == ".html"
        self.output_file(path, compress_html(content))
      else
        self.output_file(path, content)
      end
    end

    def output_js(dest, content)
      self.output_file(dest, Packr.pack(content,
        :shrink_vars => true
      ))
    end

  end



  # Overwrite old methods to insert a hook

  class Post

    include Compressor

    def write(dest)
      self.output_html(dest, self.output)
    end

  end



  class Page

    include Compressor

    def write(dest)
      self.output_html(dest, self.output)
    end

  end



  class StaticFile

    include Compressor
  	
    def write(dest)
      dest_path = self.destination(dest)

      return false if File.exist?(dest_path) and !self.modified?
      @@mtimes[path] = mtime

      case File.extname(dest_path)
        when '.html'
          self.output_html(dest_path, File.read(path))
        when '.js'
          self.output_js(dest_path, File.read(path))
        else
          FileUtils.mkdir_p(File.dirname(dest_path))
          FileUtils.cp(path, dest_path)
      end

      true
    end

  end

end