CREATE TABLE auth (
  id BIGINT NOT NULL AUTO_INCREMENT,
  sn TINYINT NOT NULL,
  user_id VARCHAR (1024),
  screen_name VARCHAR (1024),
  name VARCHAR (1024),
  image_url VARCHAR (1024),
  session CHAR (36) NOT NULL,
  oauth_token VARCHAR(1024),
  oauth_token_secret VARCHAR(1024),
  time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) DEFAULT CHARSET=utf8;
