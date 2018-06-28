import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M135 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(135)(List(
    sqlu"""
    UPDATE annotations
    SET label = 'Unlabeled'
    WHERE label = '';
    """
  ))
}
