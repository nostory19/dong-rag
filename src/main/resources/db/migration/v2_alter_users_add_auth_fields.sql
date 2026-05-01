alter table users
    add column if not exists user_password varchar(128) not null default '',
    add column if not exists user_role varchar(32) not null default 'user';

create index if not exists idx_users_user_role on users(user_role);
