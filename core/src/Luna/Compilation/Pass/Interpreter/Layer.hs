module Luna.Compilation.Pass.Interpreter.Layer where

import           Prologue                        hiding (Getter, Setter)

import           Data.Construction
import           Data.Prop
import           Data.Either                     (isRight)

import           Luna.Syntax.Term.Format           (Draft)
import           Luna.Runtime.Dynamics         (Static)
import           Luna.Syntax.Term.Expr            (Term)
import           Luna.Syntax.Model.Layer
import           Luna.Syntax.Model.Network.Class (Network)
import qualified Luna.Syntax.Model.Network.Term  as Term

import           GHC.Prim                        (Any)


type EvalMonad = IO
evalMonad = "IO"


type Errors = [String]

type Value a = Either Errors a

data InterpreterLayer = InterpreterLayer { _dirty    :: Bool
                                         , _required :: Bool
                                         , _value    :: Value (EvalMonad Any)
                                         , _time     :: Integer
                                         , _debug    :: String     -- debug data (String value)
                                         }
makeLenses ''InterpreterLayer

instance Default InterpreterLayer where
    def = InterpreterLayer True False (Left []) 0 ""

instance Show InterpreterLayer where
    show (InterpreterLayer dirty required value time _) = "InterpreterLayer{"
        <> show dirty <> ","
        <> show required <> ","
        <> valueStr <> ","
        <> show time <> "}" where
            valueStr = case value of
                           Left err -> "Left " <> show err
                           Right _  -> "Right Any"

data InterpreterData = InterpreterData deriving (Show, Eq, Ord)

type instance LayerData layout InterpreterData t = InterpreterLayer

instance Monad m => Creator    m (Layer layout InterpreterData a) where create = return $ Layer def
instance Monad m => Destructor m (Layer layout InterpreterData t) where destruct _ = return ()

instance Castable InterpreterLayer InterpreterLayer where cast = id ; {-# INLINE cast #-}
