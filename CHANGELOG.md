# Changelog

## [Unreleased]
### Fixed
- Probable agent crash on cucumber-groovy

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
