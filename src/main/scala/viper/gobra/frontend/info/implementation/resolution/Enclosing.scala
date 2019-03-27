package viper.gobra.frontend.info.implementation.resolution

import viper.gobra.ast.frontend._
import viper.gobra.frontend.info.implementation.TypeInfoImpl

trait Enclosing { this: TypeInfoImpl =>

  import viper.gobra.util.Violation._

  import decorators._

  lazy val enclosingScope: PNode => PScope =
    down((_: PNode) => violation("node does not root in a scope")) { case s: PScope => s }


  lazy val enclosingCodeRoot: PStatement => PCodeRoot =
    down((_: PNode) => violation("Statement does not root in a CodeRoot")) { case m: PCodeRoot => m }

  def typeSwitchConstraints(id: PIdnNode): Vector[PType] =
    typeSwitchConstraintsLookup(id)(id)

  lazy val typeSwitchConstraintsLookup: PIdnNode => PNode => Vector[PType] =
    paramAttr[PIdnNode, PNode, Vector[PType]] { id => {
      case tree.parent.pair(PTypeSwitchCase(left, _), s: PTypeSwitchStmt)
        if s.binder.exists(_.name == id.name) => left

      case s: PTypeSwitchStmt // Default case
        if s.binder.exists(_.name == id.name) => Vector.empty

      case tree.parent(p) => typeSwitchConstraintsLookup(id)(p)
    }
    }
}