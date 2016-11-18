{-# LANGUAGE NoMonomorphismRestriction #-}

module Luna.Parser.Literal where

import Prelude.Luna
import qualified Luna.Parser.Token        as Tok
import           Text.Parser.Combinators
import qualified Old.Luna.Syntax.Term.Expr.Lit as Lit
import           Old.Luna.Syntax.Model.Network.Builder.Term.Class hiding (number)
import qualified Old.Luna.Syntax.Model.Network.Builder.Term.Class as Term
import           Text.Parser.Char (CharParsing)
import Text.Trifecta.Combinators (DeltaParsing)
import Luna.Parser.Indent (MonadIndent)
import Text.Parser.Token (TokenParsing)
--import Luna.Parser.Builder (labeled)


--literal = choice [ numL, charL, stringL ]
--charL   = labeled (Lit.Char   <$> Tok.charLiteral)
--stringL = labeled (Lit.String <$> Tok.stringLiteral)
--numL    = labeled (Lit.Number <$> Tok.numberL)

literal = choice [ string, number ]

string :: (DeltaParsing p, MonadIndent p, TermBuilder_OLD Lit.String m a) => p (m a)
string = str <$> Tok.stringLiteral

number :: (TokenParsing p, TermBuilder_OLD Lit.Number m a) => p (m a)
number = Term.number <$> Tok.number

--str :: TermBuilder Lit.String m a => String -> m a
