# encoding: UTF-8
require 'bundler/gem_tasks'
require 'rubocop/rake_task'
require 'rspec/core/rake_task'

RSpec::Core::RakeTask.new(:spec)
RuboCop::RakeTask.new(:rubocop)

desc 'Main task for this project to ensure the project passes build'
task default: [:rubocop, :spec, :build]
