ALTER TABLE users ADD COLUMN access_scope varchar(20) NOT NULL DEFAULT 'SELECTED_FARMS';
ALTER TABLE farms ADD COLUMN boundary varchar(8000);

UPDATE users u
SET access_scope = 'ALL_FARMS'
WHERE EXISTS (
    SELECT 1 FROM user_role_grants g
    WHERE g.user_id = u.id AND g.farm_id IS NULL
);

UPDATE users u
SET access_scope = 'OWN_FARM'
WHERE u.access_scope <> 'ALL_FARMS'
  AND (
    SELECT count(*) FROM user_role_grants g
    WHERE g.user_id = u.id AND g.farm_id IS NOT NULL
  ) = 1;
