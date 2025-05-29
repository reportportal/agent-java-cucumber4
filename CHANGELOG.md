# Changelog

## [Unreleased]

## [5.3.0]
### Changed
- Client version updated on [5.3.12](https://github.com/reportportal/client-java/releases/tag/5.3.12), by @HardNorth
- Embedded data attachment level changed to "INFO", by @HardNorth

## [5.2.4]
### Changed
- Client version updated on [5.2.25](https://github.com/reportportal/client-java/releases/tag/5.2.25), by @HardNorth

## [5.2.3]
### Added
- Common Stack Trace frames skip in description and logs, by @HardNorth
- Reporting of Last Error Log in Item description, by @HardNorth and @ArtemOAS
### Changed
- Client version updated on [5.2.21](https://github.com/reportportal/client-java/releases/tag/5.2.21), by @HardNorth

## [5.2.2]
### Changed
- Client version updated on [5.2.13](https://github.com/reportportal/client-java/releases/tag/5.2.13), by @HardNorth
### Removed
- `OkHttp` dependency, by @HardNorth
- JSR-305 dependency, by @HardNorth

## [5.2.1]
### Changed
- Client version updated on [5.2.4](https://github.com/reportportal/client-java/releases/tag/5.2.4), by @HardNorth
### Removed
- `commons-model` dependency to rely on `clinet-java` exclusions in security fixes, by @HardNorth

## [5.2.0]
### Changed
- Client version updated on [5.2.0](https://github.com/reportportal/client-java/releases/tag/5.2.0), by @HardNorth

## [5.1.4]
### Changed
- Client version updated on [5.1.22](https://github.com/reportportal/client-java/releases/tag/5.1.22), by @HardNorth

## [5.1.3]
### Changed
- Client version updated on [5.1.16](https://github.com/reportportal/client-java/releases/tag/5.1.16), by @HardNorth

## [5.1.2]
### Added
- Test Case ID templating, by @HardNorth
### Changed
- Client version updated on [5.1.9](https://github.com/reportportal/client-java/releases/tag/5.1.9), by @HardNorth
- Slf4j version updated on 1.7.36, by @HardNorth

## [5.1.1]
### Fixed
- Invalid Data Tables format for some Report Portal versions
### Changed
- Client version updated on [5.1.4](https://github.com/reportportal/client-java/releases/tag/5.1.4)
- Slf4j version updated on 1.7.32 to support newer versions of Logback with security fixes

## [5.1.0]
### Changed
- Version promoted to stable release
- Client version updated on [5.1.0](https://github.com/reportportal/client-java/releases/tag/5.1.0)

## [5.1.0-RC-4]
### Added
- Feature / Scenario / Step / Hook start methods which are overridable
- JSR-305 annotations
- `buildFinishTestItemRequest` overridable method

## [5.1.0-RC-3]
### Fixed
- Probable agent crash on cucumber-groovy
### Changed
- Client version updated on [5.1.0-RC-12](https://github.com/reportportal/client-java/releases/tag/5.1.0-RC-12)

## [5.1.0-RC-2]
### Changed
- Client version updated on [5.1.0-RC-6](https://github.com/reportportal/client-java/releases/tag/5.1.0-RC-6)

## [5.1.0-RC-1]
### Changed
- Client version updated on [5.1.0-RC-4](https://github.com/reportportal/client-java/releases/tag/5.1.0-RC-4)
- Version changed on 5.1.0
### Fixed
- Scenario outlines with dynamic names support

## [5.0.3]
### Fixed
- Table parameter handling for different reporters

## [5.0.2]
### Added
- Support of cucumber versions lower than 4.4.0
### Changed
- Data attachments honor names now
- Client version updated on [5.0.21](https://github.com/reportportal/client-java/releases/tag/5.0.21)
### Fixed
- Empty interrupted suite in case of duplicate step

## [5.0.1]
### Added
- Missed annotations
### Changed
- Client version updated on [5.0.15](https://github.com/reportportal/client-java/releases/tag/5.0.15)
### Fixed
- 'CHILD_START_TIME_EARLIER_THAN_PARENT' Exception in some cases (issue #73)
- Double error message reporting (issue #71)

## [5.0.0]
### Added
- Docstring parameter handling
### Changed
- Many static methods from Util class were moved to AbstractReporter class and made protected to ease extension
- Client version updated on `5.0.12`

## [5.0.0-RC-1]
### Added
- Callback reporting
### Changed
- 'file:' prefix removed in CodeRef field
- Test step parameters handling
- Mime type processing for data embedding was improved
### Fixed
- Manually-reported nested steps now correctly fail all parents
### Removed
- Scenario Outline iteration number in item names, to not break re-runs

## [5.0.0-BETA-15]
### Fixed
- Incorrect item type settings
### Added
- Nested steps support

## [5.0.0-BETA-14]
### Added
- Test Case ID support
### Fixed
- codeRef reporting was added for every item in an item tree

## [5.0.0-BETA-13]
### Fixed
- Issue #61: Screenshots aren't displayed for failed UI tests due to missing content type of the file has been sent

## [5.0.0-BETA-12]
### Changed
- Bumping up client version
### Fixed
- Issue #56: RP Cucumber agent does not finish items due to unexpected test event status
- Issue #58: RP agent throws an exception and stuck if there are several ScenarioOutline with the identical name in the feature

## [4.0.0]
##### First release of Report Portal Java agent for Cucumber 4 for server version 4.x.x
* Initial release to Public Maven Repositories
