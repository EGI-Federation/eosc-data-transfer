# Template for EGI repositories

It includes:

- License information
- Copyright and author information
- Code of conduct and contribution guidelines
- Templates for PR and issues
- Code owners file for automatic assignment of PR reviewers
- [GitHub actions](https://github.com/features/actions) workflows for linting
  and checking links

Content is based on:

- [Contributor Covenant](http://contributor-covenant.org)
- [Semantic Versioning](https://semver.org/)
- [Chef Cookbook Contributing Guide](https://github.com/chef-cookbooks/community_cookbook_documentation/blob/master/CONTRIBUTING.MD)

## Asking for creation of a repository

It can be done by contacting the
[administrators](https://github.com/orgs/EGI-Federation/teams/admins).

The following information should be provided:

- repository name (lower case, usually not required to mention EGI in the name)
- repository description (oneliner is enough)
- optionally additional users that should be able to manage PR and issues
- optional additional requirements (like disabling Pull Requests for the initial
  repository population)

If required a private repository can be created but public ones are the default,
but feel free to ask.

Once all info will have been agreed the repository will be created.

## GitHub repository management rules

All changes should go through Pull Requests.

### Merge management

- Only squash should be enforced in the repository settings.
- Update commit message for the squashed commits as needed.

### Protection on main branch

To be configured on the repository settings.

- Require pull request reviews before merging
  - Dismiss stale pull request approvals when new commits are pushed
  - Require review from Code Owners
- Require status checks to pass before merging
  - GitHub actions if available
  - Other checks as available and relevant
  - Require branches to be up to date before merging
- Include administrators

## Access

All access should be managed via
[GitHub teams](https://github.com/orgs/EGI-Federation/teams).

- EGI-Federation/admins: administration right
- Others participants depending on the requirement: maintain, triage or write or
  rights
