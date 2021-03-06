/* NSC -- new Scala compiler
 * Copyright 2006-2011 LAMP/EPFL
 * @author  Paul Phillips
 */

package scala.tools
package util

import nsc._

/** Examines Settings and generates a bash completion file
 *  containing both bells and whistles.
 */
object BashCompletion {
  val completionTemplate = """
# Bash Scala completion
#
# Add this file to /etc/bash_completion.d/ (or your local equivalent)
# or place a line like this in your .bashrc or .profile:
#
#   .  /path/to/file/scala_completion.sh
#
# For more information, see:
#
#   http://bash-completion.alioth.debian.org/
#
# This file is generated by running scala.tools.util.BashCompletion.
#

_scala_completion_base ()
{
  cat <<EOM
@@base@@
EOM
}

_scala_completion_phaseNames ()
{
  cat <<EOM
@@phaseNames@@
EOM
}

_scala_completion_phaseSettings ()
{
  cat <<EOM
@@phaseSettings@@
EOM
}

_scala_completion_expanded_phaseSettings ()
{
  for s in $(_scala_completion_phaseSettings); do
    for n in $(_scala_completion_phaseNames); do
      echo "$s:$n"
    done
  done | sort
}

_scala_completion()
{
  local extra cur opts

  case "$1" in
         fsc) extra="@@fsc@@" ;;
       scala) extra="@@scala@@" ;;
    scaladoc) extra="@@scaladoc@@" ;;
  esac

  COMPREPLY=()
  # without dropping the :here as well,
  # completion goes wonky on -Xprint:<tab>
  COMP_WORDBREAKS=${COMP_WORDBREAKS//:}
  _get_comp_words_by_ref -n : cur prev cword

  if [[ "$cur" == *:* ]] && _scala_completion_phaseSettings | grep -q -- "${cur%%:*}" -; then
    opts=$(_scala_completion_expanded_phaseSettings)
  elif [[ -z "$cur" ]]; then  # shorten on empty
    opts="$(_scala_completion_base | grep -v ':')"
  else
    opts="$(_scala_completion_base)"
  fi

  opts="$opts $extra"
  COMPREPLY=( $(compgen -W "$opts" -- $cur) )

  return 0
} && \
complete -o default -F _scala_completion scala scalac fsc scaladoc

  """.trim

  val programSettingsMap = Map(
    "base"     -> new Settings,
    "scala"    -> new GenericRunnerSettings(_ => ()),
    "fsc"      -> new settings.FscSettings(_ => ()),
    "scaladoc" -> new doc.Settings(_ => ())
  )
  val programs = programSettingsMap.keys.toList

  def settingsOptions(name: String): Set[String] = {
    val settings = programSettingsMap.getOrElse(name, return Set())
    import settings._

    def settingStrings(s: Setting) = s match {
      case x: ChoiceSetting       => x.choices map (x.name + ":" + _)
      case x: PhasesSetting       => List(x.name + ":")
      case x                      => List(x.name)
    }

    visibleSettings flatMap settingStrings toSet
  }

  def interpolate(template: String, what: (String, String)*) =
    what.foldLeft(template) {
      case (text, (key, value)) =>
        val token = "@@" + key + "@@"

        (text indexOf token) match {
          case -1   => sys.error("Token '%s' does not exist." format token)
          case idx  => (text take idx) + value + (text drop idx drop token.length)
        }
    }

  def phasePairs() = {
    val s = new Settings
    s.usejavacp.value = true
    import s._
    List(
      "phaseNames" -> ("all" :: (new Global(s) phaseNames)),
      "phaseSettings" -> (visibleSettings collect { case x: PhasesSetting => x.name })
    )
  }

  def create() = {
    val baseOptions = settingsOptions("base")
    val pairs = programSettingsMap map {
      case ("base", _) => ("base", baseOptions.toList.sorted)
      case (name, _)   => (name, (settingsOptions(name) -- baseOptions).toList.sorted)
    }
    interpolate(
      completionTemplate,
      (pairs ++ phasePairs mapValues (_ mkString "\n")) toSeq: _*
    )
  }

  def main(args: Array[String]): Unit = {
    val result = create()
    if (result contains "@@")
      sys.error("Some tokens were not replaced: text is " + result)

    println(result)
  }
}
