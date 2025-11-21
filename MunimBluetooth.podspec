require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "MunimBluetooth"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => min_ios_version_supported, :visionos => 1.0 }
  s.source       = { :git => "https://github.com/munimtechnologies/munim-bluetooth.git", :tag => "#{s.version}" }

  s.source_files = [
    # Implementation (Swift)
    "ios/**/*.{swift}",
    # Autolinking/Registration (Objective-C++)
    "ios/**/*.{m,mm}",
    # Implementation (C++ objects)
    "cpp/**/*.{hpp,cpp}",
  ]

  autolinking_script = File.join(__dir__, "nitrogen/generated/ios/MunimBluetooth+autolinking.rb")
  if File.exist?(autolinking_script)
    load autolinking_script
    add_nitrogen_files(s)
  else
    Pod::UI.puts "[MunimBluetooth] Skipping Nitro autolinking – #{autolinking_script} not found"
  end

  s.dependency 'React-jsi'
  s.dependency 'React-callinvoker'
  install_modules_dependencies(s)
end
