# Changelog

## [Unreleased]
### Changed
- 'file:' prefix removed in CodeRef field
- Test step parameters handling
- Mime type processing for data embedding was improved
### Fixed
- Manually-reported nested steps now correctly fail all parents

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
