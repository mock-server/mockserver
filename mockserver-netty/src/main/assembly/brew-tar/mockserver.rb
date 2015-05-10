class Mockserver < Formula
  homepage "http://www.mock-server.com/"
  url "https://oss.sonatype.org/content/repositories/releases/org/mock-server/mockserver-netty/3.9.15/mockserver-netty-3.9.15-brew-tar.tar"
  version "3.9.15"
  sha256 "f4626959a6016cbf347e5f54875a94115d775f73664a35d09277f25d5acf7d47"

  depends_on :java => "1.6+"

  def install
    libexec.install Dir['*']
    bin.install_symlink "#{libexec}/bin/run_mockserver.sh" => "mockserver"

    # add lib directory soft link
    lib.install_symlink "#{libexec}/lib" => "mockserver"

    # add log directory soft link
    mockserver_log = var/"log"/"mockserver"
    mockserver_log.mkpath

    libexec.install_symlink mockserver_log => "log"
  end
end
