-- One-time cleanup of the couples created while proving the project worked end to end.
--
-- Targeted at those specific invite codes on purpose: a migration that deleted couples
-- broadly would be a loaded gun sitting in the history of a two-person app.
delete from couples where invite_code in ('7WHZDC', 'SYLGFD', 'NP22GK');
