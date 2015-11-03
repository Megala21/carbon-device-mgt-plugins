-- -----------------------------------------------------
-- Table `WIN_DEVICE`
-- -----------------------------------------------------
CREATE TABLE WIN_DEVICE (
  DEVICE_ID VARCHAR(45) NOT NULL,
  PUSH_TOKEN VARCHAR(45) DEFAULT NULL,
  IMEI VARCHAR(45) DEFAULT NULL,
  IMSI VARCHAR(45) DEFAULT NULL,
  OS_VERSION VARCHAR(45) DEFAULT NULL,
  DEVICE_MODEL VARCHAR(45) DEFAULT NULL,
  VENDOR VARCHAR(45) DEFAULT NULL,
  LATITUDE VARCHAR(45) DEFAULT NULL,
  LONGITUDE VARCHAR(45) DEFAULT NULL,
  CHALLENGE VARCHAR(45) DEFAULT NULL,
  TOKEN VARCHAR(50) DEFAULT NULL,
  UNLOCK_TOKEN VARCHAR(2000) DEFAULT NULL,
  SERIAL VARCHAR(45) DEFAULT NULL,
  PRIMARY KEY (DEVICE_ID)
)
/

-- -----------------------------------------------------
-- Table `WIN_FEATURE`
-- -----------------------------------------------------
CREATE TABLE WIN_FEATURE (
  ID INT NOT NULL,
  CODE VARCHAR(45) NOT NULL,
  NAME VARCHAR(100) NOT NULL,
  DESCRIPTION VARCHAR(200) NULL,
  PRIMARY KEY (ID)
)
/

-- -----------------------------------------------------
-- Sequence `WIN_FEATURE_ID_INC_SEQ`
-- -----------------------------------------------------
CREATE SEQUENCE WIN_FEATURE_ID_INC_SEQ START WITH 1 INCREMENT BY 1 NOCACHE
/

-- -----------------------------------------------------
-- Trigger `WIN_FEATURE_ID_INC_TRIG`
-- -----------------------------------------------------
CREATE OR REPLACE TRIGGER WIN_FEATURE_ID_INC_TRIG
BEFORE INSERT
ON WIN_FEATURE
REFERENCING NEW AS NEW
FOR EACH ROW
  BEGIN
    SELECT WIN_FEATURE_ID_INC_SEQ.NEXTVAL INTO :NEW.ID FROM DUAL;
  END;
/