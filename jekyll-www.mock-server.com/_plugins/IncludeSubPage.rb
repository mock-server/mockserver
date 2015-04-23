# encoding: UTF-8

class IncludeSubPageTag < Liquid::Tag
  def initialize(tag_name, file, tokens)
    super
    @file = file.strip
  end

  def page_path(context)
    context.registers[:page].nil? ? includes_dir : File.dirname(context.registers[:page]["path"])
  end

  def resolved_includes_dir(context)
    context.registers[:site].in_source_dir(page_path(context))
  end

  def file_read_opts(context)
    context.registers[:site].file_read_opts
  end

  def read_file(file, context)
    File.read(file, file_read_opts(context))
  end

  def render(context)
    dir = resolved_includes_dir(context)

    file = @file
    path = File.join(dir, file)

    file_contents = read_file(path, context).gsub(/\-{3}.*\-{3}/m, '')
    partial = Liquid::Template.parse(file_contents)

    context.stack do
      context['include'] = parse_params(context) if @params
      partial.render!(context)
    end
  end
end

Liquid::Template.register_tag('include_subpage', IncludeSubPageTag)

class IncludeSubPageAbsoluteTag < Liquid::Tag
  def initialize(tag_name, file, tokens)
    super
    @file = file.strip
  end

  def page_path(context)
    context.registers[:page].nil? ? includes_dir : File.dirname(context.registers[:page]["path"])
  end

  def file_read_opts(context)
    context.registers[:site].file_read_opts
  end

  def read_file(file, context)
    File.read(file, file_read_opts(context))
  end

  def render(context)
    dir = '_includes/..'

    file = @file
    path = File.join(dir, file)

    file_contents = read_file(path, context).gsub(/\-{3}.*\-{3}/m, '')
    partial = Liquid::Template.parse(file_contents)

    context.stack do
      context['include'] = parse_params(context) if @params
      partial.render!(context)
    end
  end
end

Liquid::Template.register_tag('include_subpage_absolute', IncludeSubPageTag)
