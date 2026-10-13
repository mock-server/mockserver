import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import type { SxProps, Theme } from '@mui/material/styles';
import type { OpenApiLoadProblem } from '../lib/openapiImport';
import { monospaceFontFamily } from '../theme';

/** Enough to act on; the import response holds every problem. */
const MAX_SHOWN = 50;

interface OpenApiLoadProblemsAlertProps {
  problems: OpenApiLoadProblem[];
  sx?: SxProps<Theme>;
}

/**
 * Lists the problems of an OpenAPI spec that loaded but breaks the OpenAPI rules,
 * grouped by kind, each with its JSON-pointer location and any hint. Renders
 * nothing when there are no problems.
 */
export default function OpenApiLoadProblemsAlert({ problems, sx }: OpenApiLoadProblemsAlertProps) {
  if (problems.length === 0) {
    return null;
  }
  const shown = problems.slice(0, MAX_SHOWN);
  const groups = new Map<string, OpenApiLoadProblem[]>();
  for (const problem of shown) {
    const group = groups.get(problem.kindLabel);
    if (group) {
      group.push(problem);
    } else {
      groups.set(problem.kindLabel, [problem]);
    }
  }
  const count = problems.length;
  return (
    <Alert severity="warning" variant="outlined" sx={sx} data-testid="openapi-load-problems">
      <AlertTitle>
        Spec Loaded With {count} Problem{count === 1 ? '' : 's'}
      </AlertTitle>
      <Typography variant="body2" sx={{ mb: 1 }}>
        These parts of the spec break the OpenAPI rules, so the expectations generated from them may
        not behave as expected.
      </Typography>
      {[...groups.entries()].map(([kindLabel, group]) => (
        <Box key={kindLabel} sx={{ mb: 1 }}>
          <Typography variant="subtitle2" component="p">
            {kindLabel} ({group.length})
          </Typography>
          <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
            {group.map((problem, index) => (
              <Box component="li" key={`${problem.specIndex ?? 0}-${problem.location ?? ''}-${index}`} sx={{ mb: 0.5 }}>
                <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
                  {problem.location && (
                    <Box component="span" sx={{ fontFamily: monospaceFontFamily, fontSize: '0.78rem' }}>
                      {problem.location}
                      {': '}
                    </Box>
                  )}
                  {problem.description}
                </Typography>
                {problem.hint && (
                  <Typography variant="caption" color="text.secondary" component="p">
                    Hint: {problem.hint}
                  </Typography>
                )}
              </Box>
            ))}
          </Box>
        </Box>
      ))}
      {count > MAX_SHOWN && (
        <Typography variant="caption" component="p">
          {count - MAX_SHOWN} more not shown; PUT /mockserver/openapi?includeLoadProblems=true returns every problem.
        </Typography>
      )}
    </Alert>
  );
}
