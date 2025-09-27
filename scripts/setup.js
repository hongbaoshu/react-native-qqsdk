// scripts/setup.js
// 用于自动配置 iOS/Android 项目，替代 postlink.js，供用户手动执行

const fs = require('fs');
const glob = require('glob');
const inquirer = require('inquirer');
const xcode = require('xcode');
const path = require('path');
const plist = require('plist');
const _ = require('lodash');
const pbxFile = require('xcode/lib/pbxFile');
const packageJson = require('../../package.json');

const ignoreNodeModules = { ignore: 'node_modules/**' };
const appDelegatePaths = glob.sync('**/AppDelegate.m', ignoreNodeModules);

// 检查文件存在性，避免异常
function safeReadFileSync(filePath, encoding = 'utf8') {
  try {
    return fs.readFileSync(filePath, encoding);
  } catch (e) {
    console.warn(`[setup.js] 文件不存在: ${filePath}`);
    return '';
  }
}

function findFileByAppName(array, appName) {
  if (array.length === 0 || !appName) return null;
  for (let i = 0; i < array.length; i++) {
    const p = array[i];
    if (p && p.indexOf(appName) !== -1) {
      return p;
    }
  }
  return null;
}

const appDelegatePath = findFileByAppName(appDelegatePaths, packageJson ? packageJson.name : null) || appDelegatePaths[0];
const plistPath = glob.sync(path.join(path.dirname(appDelegatePath), '*Info.plist').replace(/\\/g, '/'), ignoreNodeModules)[0];
let appDelegateContents = safeReadFileSync(appDelegatePath);
let plistContents = safeReadFileSync(plistPath);
let skipAddAppId = false;
let addTypes = false;
const qqSchemes = [
  'mqqapi','mqq','mqqOpensdkSSoLogin','mqqconnect','mqqopensdkdataline',
  'mqqopensdkgrouptribeshare', 'mqqopensdkfriend','mqqopensdkapi','mqqopensdkapiV2',
  'mqqopensdkapiV3','mqzoneopensdk','wtloginmqq','wtloginmqq2', 'mqqwpa','mqzone',
  'mqzonev2','mqzoneshare','wtloginqzone','mqzonewx','mqzoneopensdkapiV2',
  'mqzoneopensdkapi19','mqzoneopensdkapi', 'mqzoneopensdk','mqqopensdkapiv4'
];

function addRCTLinkManagerHeader() {
  if (!appDelegatePath || !appDelegateContents) return;
  const linkHeaderImportStatement = '#import <React/RCTLinkingManager.h>';
  if (~appDelegateContents.indexOf(linkHeaderImportStatement)) {
    console.log('"RCTLinkingManager.h" header already imported.');
  } else {
    const appDelegateHeaderImportStatement = '#import "AppDelegate.h"';
    appDelegateContents = appDelegateContents.replace(appDelegateHeaderImportStatement,
      `${appDelegateHeaderImportStatement}\n${linkHeaderImportStatement}`);
  }
  fs.writeFileSync(appDelegatePath, appDelegateContents);
}

function addLinkFunction() {
  if (!appDelegatePath || !appDelegateContents) return;
  const linkFunctionName = '- (BOOL)application:(UIApplication *)application openURL:(NSURL *)url\n    sourceApplication:(NSString *)sourceApplication annotation:(id)annotation'.replace(/(\r\n|\n|\r)/gm, '').replace(/\s/g, '').trim();
  const linkFunction = `- (BOOL)application:(UIApplication *)application openURL:(NSURL *)url\n    sourceApplication:(NSString *)sourceApplication annotation:(id)annotation\n  {\n    return [RCTLinkingManager application:application openURL:url\n                        sourceApplication:sourceApplication annotation:annotation];\n  }`;
  if (~appDelegateContents.replace(/(\r\n|\n|\r)/gm, '').replace(/\s/g, '').trim().indexOf(linkFunctionName)) {
    console.log('link function already imported.');
  } else {
    const appDelegateEndStatement = '@end';
    appDelegateContents = appDelegateContents.replace(appDelegateEndStatement,
      `${linkFunction}\n${appDelegateEndStatement}`);
  }
  fs.writeFileSync(appDelegatePath, appDelegateContents);
}

function findAppID(types) {
  return _.findIndex(types, function(schemes) {
    return -1 !== _.findIndex(schemes.CFBundleURLSchemes, function (scheme) {
      return scheme.startsWith('tencent');
    });
  });
}

function addURLTypesForTencentSDK() {
  if (skipAddAppId) {
    console.log('发现已经存在AppID');
  } else {
    inquirer.prompt([{
      type: 'input',
      name: 'AppID',
      message: 'What is your Tencent SDK AppID for iOS (hit <ENTER> to ignore)'
    }]).then(function(answer) {
      const key = ('tencent' + answer.AppID) || 'app-id-here';
      const qqAppId = {
        CFBundleURLName: 'qqAppId',
        CFBundleTypeRole: 'Editor',
        CFBundleURLSchemes: [key]
      };
      const parsedInfoPlist = plist.parse(plistContents);
      if (addTypes) {
        parsedInfoPlist.CFBundleURLTypes = [];
      }
      parsedInfoPlist.CFBundleURLTypes.push(qqAppId);
      plistContents = plist.build(parsedInfoPlist);
      fs.writeFileSync(plistPath, plistContents);
    }).then(function() {
      addAppIdToPackageJson();
    });
  }
}

function addAppID() {
  const parsedInfoPlist = plist.parse(plistContents);
  const types = parsedInfoPlist.CFBundleURLTypes;
  types ? (skipAddAppId = findAppID(types) === -1 ? false : true) : addTypes = true;
  addURLTypesForTencentSDK();
}

function addQueriesSchemes() {
  if (!plistPath || !plistContents) return;
  const parsedInfoPlist = plist.parse(plistContents);
  const schemes = parsedInfoPlist.LSApplicationQueriesSchemes;
  parsedInfoPlist.LSApplicationQueriesSchemes = schemes ? _.union(schemes, qqSchemes) : qqSchemes;
  plistContents = plist.build(parsedInfoPlist);
  fs.writeFileSync(plistPath, plistContents);
}

function addFrameworkAndSearchPath() {
  const projectPath = glob.sync('**/project.pbxproj', ignoreNodeModules)[0];
  if (!projectPath) return;
  const project = xcode.project(projectPath);
  const frameworkPath = path.join(__dirname, '../node_modules/react-native-qqsdk/ios/RCTQQSDK/TencentOpenAPI.framework');
  const project_dir = path.join(__dirname);
  const project_relative = path.relative(project_dir, frameworkPath);
  project.parse(function (error) {
    if (error) {
      console.log('xcode project error is', error);
    } else {
      const target = project.getFirstTarget().uuid;
      const file = new pbxFile(project_relative, { customFramework: true, target });
      file.uuid = project.generateUuid();
      file.fileRef = project.generateUuid();
      file.target = target;
      if (project.hasFile(file.path)) return false;
      project.addToPbxBuildFileSection(file);        // PBXBuildFile
      project.addToPbxFileReferenceSection(file);    // PBXFileReference
      project.addToFrameworksPbxGroup(file);         // PBXGroup
      project.addToPbxFrameworksBuildPhase(file);    // PBXFrameworksBuildPhase
      addSearchPaths(project, '"$(SRCROOT)/../node_modules/react-native-qqsdk/ios/RCTQQSDK/**"');
      fs.writeFileSync(projectPath, project.writeSync());
    }
  });
}

function addSearchPaths(project, frameworkSearchPath) {
  const config = project.pbxXCBuildConfigurationSection();
  const INHERITED = '"$(inherited)"';
  Object.keys(config)
    .filter(ref => ref.indexOf('_comment') === -1)
    .forEach(ref => {
      const buildSettings = config[ref].buildSettings;
      const shouldVisitBuildSettings = (buildSettings['PRODUCT_NAME'] === packageJson.name);
      if (shouldVisitBuildSettings) {
        if (!buildSettings['FRAMEWORK_SEARCH_PATHS'] || buildSettings['FRAMEWORK_SEARCH_PATHS'] === INHERITED) {
          buildSettings['FRAMEWORK_SEARCH_PATHS'] = [INHERITED];
        }
        const framworkIndex = _.findIndex(buildSettings['FRAMEWORK_SEARCH_PATHS'], function(p) { return p == frameworkSearchPath; });
        if (framworkIndex === -1) {
          buildSettings['FRAMEWORK_SEARCH_PATHS'].push(frameworkSearchPath);
        }
      }
    });
}

function addAppIdToPackageJson() {
  const packagePath = path.join(__dirname, '../../package.json');
  const packageFile = fs.readFileSync(packagePath, 'utf8');
  inquirer.prompt([{
    type: 'input',
    name: 'AppID',
    message: 'What is your Tencent SDK AppID for Android (hit <ENTER> to ignore)'
  }]).then(function(answer) {
    const key = answer.AppID || 'app-id-here';
    const pkg = JSON.parse(packageFile);
    pkg.qq_app_id = key;
    fs.writeFileSync(packagePath, JSON.stringify(pkg, null, 2));
  });
}

function addAppIdToGradle() {
  const buildGradlePath = path.join(__dirname, '../../android/build.gradle');
  let buildGradleContents = fs.readFileSync(buildGradlePath, 'utf8');
  const appIDLink = '${QQ_APP_ID}';
  if (~buildGradleContents.indexOf(appIDLink)) {
    inquirer.prompt([{
      type: 'input',
      name: 'AppID',
      message: 'What is your Tencent SDK AppID for Android (hit <ENTER> to ignore)'
    }]).then(function(answer) {
      const key = answer.AppID || 'app-id-here';
      buildGradleContents = buildGradleContents.replace(appIDLink, `${key}`);
      fs.writeFileSync(buildGradlePath, buildGradleContents);
    });
  } else {
    console.log('App ID 可能已存在，如果要进行修改请在react-native-qqsdk的Gradle文件中手动修改Android App ID');
  }
}

// 执行所有自动化步骤
addRCTLinkManagerHeader();
addLinkFunction();
addFrameworkAndSearchPath();
addAppID();
addQueriesSchemes();
// addAppIdToGradle(); // 如需自动写入 Android AppID 可取消注释

console.log('\n[react-native-qqsdk] setup.js 执行完毕。\n如有任何步骤未自动完成，请参考 README 手动补充配置。');
