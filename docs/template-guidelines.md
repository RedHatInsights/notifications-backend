# Template Guidelines

## Template Resolution Order

`TemplateService.compileTemplate()` resolves templates using a three-level fallback chain. A `TemplateDefinition` is a record of `(IntegrationType, bundle, application, eventType, isBetaVersion)`. Resolution proceeds:

1. Exact match on all four fields (integration type + bundle + application + event type).
2. Application-level default: same integration type + bundle + application, `eventType=null`.
3. System-level default: same integration type only, `bundle=null`, `application=null`, `eventType=null`.
4. If the definition is a beta version and no match is found at any level, the entire chain is retried with `isBetaVersion=false`.

If no template is found after all fallbacks, `TemplateNotFoundException` is thrown. Every template used in production must be registered in a mapping class; unregistered files are dead code.

## Template File Organization

All template files live under `common-template/src/main/resources/templates/`. The first-level directory is the **channel** (matching `IntegrationType.getRootFolder()`):

| IntegrationType enum value | Root folder | File format |
|---|---|---|
| `DRAWER` | `drawer/` | `.md` (Markdown) |
| `EMAIL_BODY`, `EMAIL_TITLE`, `EMAIL_DAILY_DIGEST_BODY`, `EMAIL_DAILY_DIGEST_BUNDLE_AGGREGATION_BODY`, `EMAIL_DAILY_DIGEST_BUNDLE_AGGREGATION_TITLE` | `email/` | `.html` or `.txt` |
| `SLACK` | `slack/` | `.json` |
| `MS_TEAMS` | `ms_teams/` | `.json` |
| `GOOGLE_CHAT` | `google_chat/` | `.json` |

Under each channel directory, templates are organized by **application folder** (PascalCase, e.g., `Advisor/`, `CostManagement/`, `OCM/`). Shared layout templates go in `Common/` and fallback defaults in `Default/`.

### Naming Conventions

- Drawer body: `{eventNameCamelCase}Body.md` (e.g., `newRecommendationBody.md`)
- Instant email body: `{eventNameCamelCase}InstantEmailBody.html`
- Daily digest body: `dailyEmailBody.html`
- Email title: `instantEmailTitle.txt`
- Slack/Teams/Google Chat: `{eventNameCamelCase}.json` or `default.json`

## Registering a Template

Templates are registered in static `Map<TemplateDefinition, String>` fields inside mapping classes under `com.redhat.cloud.notifications.qute.templates.mapping`. Each mapping class corresponds to a product bundle:

| Mapping class | Bundle |
|---|---|
| `Rhel` | `rhel` |
| `OpenShift` | `openshift` |
| `Console` | `console` |
| `SubscriptionServices` | `subscription-services` |
| `AnsibleAutomationPlatform` | `ansible-automation-platform` |
| `DefaultTemplates` | System defaults (bundle=null) |
| `SecureEmailTemplates` | Secured environment overrides |
| `DefaultInstantEmailTemplates` | Stage-only fallback (bundle=null) |

The map value is the path **relative to the channel root folder** (e.g., `"Advisor/newRecommendationBody.md"`). `TemplateService.buildTemplateFilePath()` prepends the channel root folder.

### Adding a New Event Type Template

1. Define the event type constant in the appropriate mapping class (e.g., `Rhel`).
2. Create the template file(s) in the correct channel/application folder.
3. Add `entry(new TemplateDefinition(...), "FolderName/fileName")` to the mapping class's `templatesMap`.
4. At startup, `TemplateService.init()` calls `checkTemplatesConsistency()`, which verifies every registered path exists on the classpath and is parseable by Qute. Missing files cause startup failure.

## Template Composition Patterns

### Drawer Templates

Drawer templates use Qute `{#include}` to wrap content in `drawer/Common/commonDrawerNotification.md`, which declares the `query_params` parameter (`from=notifications&integration=drawer`):

```
{#include drawer/Common/commonDrawerNotification.md}
{#body}
**[{data.context.display_name}]({environment.url}/path?{query_params})** has {data.events.size()} new items.
{/body}
{/include}
```

Data is accessed via `{data.*}` (the action transformed to a map by `BaseTransformer.toJsonObject()`).

### Email Templates

Email body templates use `{#include email/Common/insightsEmailBody}` and fill named sections: `content-header-title`, `content-title`, `content-title-right-part`, `content-body`, `content-button-href`, `content-button-service-name`. The parent template declares style parameters as `{@String ...}` variables. Data is accessed via `{action.*}` (the `Action` object or map passed directly).

### Slack, MS Teams, Google Chat

Each channel has a shared layout in its `Common/` folder (`commonSlackBlocks.json`, `commonMsAdaptiveCard.json`, `commonCardsV2.json`). Default templates simply include the common layout: `{#include slack/Common/commonSlackBlocks.json /}`. Data is accessed via `{data.*}`.

## Qute Syntax Conventions

- **Parameter declarations**: `{@String varName = "default"}` at the top of templates.
- **Conditionals**: `{#if expr}`, `{#else}`, `{/if}`; null-safe checks with `{#if field??}`.
- **Loops**: `{#each collection}...{it.field}...{/each}`.
- **Switch**: `{#switch expr}{#case "val"}...{/switch}`.
- **Size**: `{list.size()}`, pluralization via `{#if list.size() is 1}`.
- **Or-default**: `{field.or('')}` for null-safe defaults.
- **Raw output**: `{value.raw}` to skip HTML escaping (used for URLs).
- **Safe output**: `{value.safe}` to mark trusted HTML (used in pendo messages).

## Template Extension Classes

Extensions are in `com.redhat.cloud.notifications.qute.templates.extensions` and are auto-discovered by Qute via the `@TemplateExtension` annotation.

| Extension class | Methods available in templates |
|---|---|
| `ActionExtension` | `context.propertyName` (dynamic property access on `Context`), `payload.propertyName` (on `Payload`), `action.toPrettyJson()` |
| `LocalDateTimeExtension` | `date.toUtcFormat()`, `date.toStringFormat()`, `date.toTimeAgo()`, `string.toUtcFormat()`, `string.toStringFormat()`, `string.toTimeAgo()` |
| `SeverityExtension` | `string.severityAsEmailTitle()`, `string.toTitleCase()`, `string.asSeverityEmoji()`, `string.asPatternFlySeverity()` |
| `UrlEncodingExtension` | `string.urlEncode()` |
| `ErrataSortExtension` | `list.sortErrataSecurityArray()`, `list.sortErrataArray()` |
| `ApplicationServicesSortExtension` | `map.sortByProductDescription()`, `list.sortByEventTypeDisplayName()` |

To add a new extension, create a class in the `extensions` package with static methods annotated `@TemplateExtension`. Use `matchName = TemplateExtension.ANY` for dynamic property access patterns.

## TemplateService Rendering API

`TemplateService` is an `@ApplicationScoped` CDI bean initialized at `@Startup`.

| Method | Use case |
|---|---|
| `renderTemplate(TemplateDefinition, Map<String, Object>)` | Drawer/Slack/Teams/Chat: data map is passed under `"data"` key |
| `renderTemplateWithCustomDataMap(TemplateDefinition, Map<String, Object>)` | Email: full context map with keys like `action`, `environment`, `pendo_message`, `ignore_user_preferences` |
| `renderTemplateWithCustomDataMap(String templateContent, Map)` | Inline template rendering (used for dynamic content) |
| `isValidTemplateDefinition(TemplateDefinition)` | Checks if a template exists without throwing |
| `convertActionToContextMap(Action)` | Converts `Action.context` to `Map` for aggregation templates |

### Configuration Flags

- `notifications.use-secured-email-templates.enabled` (default `false`): Loads `SecureEmailTemplates` instead of the standard mapping classes. Used in restricted environments.
- `notifications.use-default-template` (default `false`): Adds `DefaultInstantEmailTemplates` as a catch-all `EMAIL_BODY` fallback. Stage-only.

## Testing Templates

Tests live in `common-template/src/test/java/` mirroring channels: `drawer/`, `email/`, `google_chat/`, `ms_teams/`, `slack/`.

### Drawer Test Pattern

```java
@QuarkusTest
class TestMyAppTemplate {
    @Inject TestHelpers testHelpers;

    @Test
    void testRenderedTemplate() {
        Action action = TestHelpers.createSomeAction(...);
        TemplateDefinition def = new TemplateDefinition(
            IntegrationType.DRAWER, "bundle", "app", "event-type");
        String result = testHelpers.renderTemplate(def, action);
        assertEquals("Expected rendered markdown", result);
    }
}
```

`TestHelpers.renderTemplate(TemplateDefinition, Action)` transforms the `Action` through `BaseTransformer.toJsonObject()` (same as production `DrawerProcessor`) and passes it to `TemplateService.renderTemplateWithCustomDataMap()`.

### Email Test Pattern

Extend `EmailTemplatesRendererHelper` and override `getBundle()`, `getApp()`, `getAppDisplayName()`. Use `generateEmailBody(eventType, action)` for instant emails and `generateAggregatedEmailBody(jsonContext)` for daily digests. Assert on `result.contains(...)` for expected content. Test output is written to `target/` as HTML files for visual inspection.

### Test Helper Classes

Action factory methods are in `helpers/TestHelpers.java` (e.g., `createAdvisorAction()`, `createComplianceAction()`). Domain-specific helpers exist in `helpers/` (e.g., `ErrataTestHelpers`, `PatchTestHelpers`, `InventoryTestHelpers`).

## Verification Commands

```bash
# Run all template tests (drawer, email, slack, teams, google chat)
./mvnw test -pl common-template

# Run tests for a specific channel
./mvnw test -pl common-template -Dtest="drawer.*"

# Run a single template test class
./mvnw test -pl common-template -Dtest="drawer.TestAdvisorTemplate"

# Verify template consistency (all registered templates parse at startup)
./mvnw test -pl common-template -Dtest="TemplateServiceTest"

# Inspect rendered email output after tests (written to target/)
ls common-template/target/email/
```
